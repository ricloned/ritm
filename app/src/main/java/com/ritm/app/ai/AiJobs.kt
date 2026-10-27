package com.ritm.app.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ritm.app.MainActivity
import com.ritm.app.R
import com.ritm.app.data.Codec
import com.ritm.app.data.Store
import com.ritm.app.data.mapObjects
import com.ritm.app.data.toJsonArray
import com.ritm.app.ui.UiState
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Все запросы к ИИ идут через этот объект:
 * - пока есть хоть один запрос, работает фоновый сервис — Android не убивает приложение, когда его свернули;
 * - незавершённые запросы запоминаются на диске и докручиваются при следующем запуске;
 * - результаты планера и программы тоже сохраняются, поэтому не пропадают.
 */
object AiJobs {
    private const val PREFS = "ai_jobs"
    private lateinit var ctx: Context
    private var ready = false
    private val active = HashSet<String>()
    private val jobs = HashMap<String, kotlinx.coroutines.Job>()

    fun init(context: Context) {
        if (ready) return
        ctx = context.applicationContext
        ready = true
        loadResults()
    }

    private fun prefs() = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pending(): Set<String> = prefs().getStringSet("pending", emptySet()) ?: emptySet()

    private fun setPending(keys: Set<String>) {
        prefs().edit().putStringSet("pending", HashSet(keys)).commit()
    }

    fun isRunning(key: String) = synchronized(active) { key in active }

    /** Запускает запрос [key]. Повторный запуск того же ключа игнорируется. */
    fun run(key: String, block: suspend () -> Unit) {
        if (!ready) {
            Store.scope.launch { block() }
            return
        }
        synchronized(active) {
            if (key in active) return
            active.add(key)
        }
        setPending(pending() + key)
        startService()
        val job = Store.scope.launch {
            try {
                block()
            } finally {
                finish(key)
            }
        }
        synchronized(active) { if (key in active) jobs[key] = job }
    }

    private fun finish(key: String) {
        setPending(pending() - key)
        val empty = synchronized(active) {
            active.remove(key)
            jobs.remove(key)
            active.isEmpty()
        }
        if (empty) stopService()
    }

    /** Отмена запроса: сразу освобождаем кнопку, соединение обрываем. */
    fun cancel(key: String) {
        val job = synchronized(active) { jobs[key] }
        job?.cancel()
        finish(key)
        AiClient.abortAll()
        when (key) {
            "program" -> { UiState.programLoading = false; UiState.programError = null }
            "planner" -> { UiState.plannerLoading = false; UiState.plannerError = null }
            "nutrition" -> { UiState.nutritionLoading = false; UiState.nutritionError = null }
        }
    }

    private fun errText(e: Exception): String? =
        if (e is kotlinx.coroutines.CancellationException) null else (e.message ?: "Ошибка")

    /** При запуске приложения докручиваем запросы, которые оборвались (приложение закрыли/убили). */
    fun resume() {
        if (!ready) return
        val all = pending()
        val running = synchronized(active) { HashSet(active) }
        // то, что ещё идёт в этом процессе (например, после поворота экрана), не трогаем
        val keys = all - running
        if (keys.isEmpty()) return
        setPending(all intersect running)
        keys.forEach { key ->
            val id = key.substringAfter(':', "").toLongOrNull()
            when {
                key.startsWith("food:") && id != null -> Store.analyzeFood(id)
                key.startsWith("report:") && id != null -> Store.generateReport(id)
                key.startsWith("chat:") && id != null -> Store.answerChat(id)
                key == "planner" -> prefs().getString("plannerRequest", null)?.let { startPlanner(it) }
                key == "program" -> prefs().getString("programRequest", null)?.let { startProgram(it) }
                key == "nutrition" -> startNutrition()
            }
        }
    }

    // ---------------- Планер ----------------

    fun startPlanner(request: String) {
        if (request.isBlank()) return
        prefs().edit().putString("plannerRequest", request).apply()
        UiState.plannerLoading = true
        UiState.plannerError = null
        UiState.aiStartedAt = UiState.aiStartedAt + ("planner" to System.currentTimeMillis())
        run("planner") {
            try {
                val res = AiClient.parsePlan(request)
                UiState.plannerItems = res
                savePlannerItems(res)
                if (res.isEmpty()) UiState.plannerError = "Не нашёл задач в тексте"
            } catch (e: Exception) {
                UiState.plannerError = errText(e)
            } finally {
                UiState.plannerLoading = false
            }
        }
    }

    fun savePlannerItems(items: List<AiClient.PlanItem>) {
        if (!ready) return
        val arr = items.toJsonArray { p ->
            JSONObject()
                .put("title", p.title)
                .put("date", p.date.toString())
                .put("time", p.time?.toString() ?: JSONObject.NULL)
                .put("selected", p.selected)
        }
        prefs().edit().putString("plannerItems", arr.toString()).apply()
    }

    // ---------------- Норма КБЖУ ----------------

    fun startNutrition() {
        UiState.nutritionLoading = true
        UiState.nutritionError = null
        UiState.aiStartedAt = UiState.aiStartedAt + ("nutrition" to System.currentTimeMillis())
        run("nutrition") {
            try {
                val r = AiClient.nutritionPlan()
                Store.updateSettings(
                    Store.settings.copy(
                        kcalGoal = r.kcal, proteinGoal = r.protein, fatGoal = r.fat, carbsGoal = r.carbs,
                    )
                )
                UiState.nutritionComment = r.comment
                prefs().edit().putString("nutritionComment", r.comment).apply()
            } catch (e: Exception) {
                UiState.nutritionError = errText(e)
            } finally {
                UiState.nutritionLoading = false
            }
        }
    }

    // ---------------- Программа ----------------

    fun startProgram(request: String) {
        if (request.isBlank()) return
        prefs().edit().putString("programRequest", request).apply()
        UiState.programLoading = true
        UiState.programError = null
        UiState.aiStartedAt = UiState.aiStartedAt + ("program" to System.currentTimeMillis())
        run("program") {
            try {
                val r = AiClient.buildProgram(request, Store.program) { UiState.programStep = it }
                UiState.programResult = r
                saveProgramResult(r)
            } catch (e: Exception) {
                UiState.programError = errText(e)
            } finally {
                UiState.programLoading = false
                UiState.programStep = null
            }
        }
    }

    fun saveProgramResult(r: AiClient.ProgramResult?) {
        if (!ready) return
        val e = prefs().edit()
        if (r == null) e.remove("programResult") else {
            e.putString(
                "programResult",
                JSONObject().put("days", Codec.programToJson(r.days)).put("comment", r.comment).toString()
            )
        }
        e.apply()
    }

    private fun loadResults() {
        UiState.nutritionComment = prefs().getString("nutritionComment", null)
        try {
            prefs().getString("plannerItems", null)?.let { s ->
                val today = LocalDate.now()
                UiState.plannerItems = JSONArray(s).mapObjects { o ->
                    AiClient.PlanItem(
                        title = o.getString("title"),
                        date = LocalDate.parse(o.getString("date")).let { if (it.isBefore(today)) today else it },
                        time = if (o.isNull("time")) null else LocalTime.parse(o.getString("time")),
                        selected = o.optBoolean("selected", true),
                    )
                }
            }
        } catch (_: Exception) {
        }
        try {
            prefs().getString("programResult", null)?.let { s ->
                val o = JSONObject(s)
                UiState.programResult = AiClient.ProgramResult(
                    Codec.programFromJson(o.optJSONObject("days")),
                    o.optString("comment"),
                )
            }
        } catch (_: Exception) {
        }
    }

    // ---------------- Фоновый сервис ----------------

    private fun startService() {
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, AiService::class.java))
        } catch (_: Exception) {
            // запуск из фона запрещён — запрос всё равно идёт, просто без «щита»
        }
    }

    private fun stopService() {
        try {
            ctx.stopService(Intent(ctx, AiService::class.java))
        } catch (_: Exception) {
        }
    }
}

/** Держит приложение живым, пока ИИ отвечает. Показывает тихое уведомление. */
class AiService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL, "ИИ работает", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Показывается, пока ИИ отвечает, чтобы запрос не оборвался"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 11, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ИИ думает…")
            .setContentText("Можно свернуть приложение — ответ сохранится")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "ai_work"
        private const val NOTIF_ID = 100010
    }
}
