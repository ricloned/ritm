package com.ritm.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ritm.app.ai.AiClient
import com.ritm.app.notify.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Всё состояние приложения. Хранится в одном JSON-файле во внутренней памяти.
 */
object Store {
    lateinit var appContext: Context
        private set
    private lateinit var file: File
    private val io = Executors.newSingleThreadExecutor()
    private var loaded = false
    private var lastId = 0L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var tasks by mutableStateOf(listOf<Task>())
        private set
    var program by mutableStateOf(mapOf<Int, List<Exercise>>())
        private set
    var logs by mutableStateOf(listOf<WorkoutLog>())
        private set
    var draft by mutableStateOf<WorkoutDraft?>(null)
        private set
    var food by mutableStateOf(listOf<FoodEntry>())
        private set
    var goals by mutableStateOf(listOf<Goal>())
        private set
    var settings by mutableStateOf(AppSettings())
        private set

    /** Черновики текста для ИИ — не теряются при переключении вкладок и перезапуске. */
    var plannerDraft by mutableStateOf("")
        private set
    var programDraft by mutableStateOf("")
        private set

    var reportLoading by mutableStateOf(setOf<Long>())
        private set
    var reportErrors by mutableStateOf(mapOf<Long, String>())
        private set

    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        appContext = ctx.applicationContext
        file = File(appContext.filesDir, "data.json")
        load()
        loaded = true
    }

    @Synchronized
    fun newId(): Long {
        val t = System.currentTimeMillis()
        lastId = if (t > lastId) t else lastId + 1
        return lastId
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            tasks = o.optJSONArray("tasks").mapObjects(Codec::taskFromJson)
            program = Codec.programFromJson(o.optJSONObject("program"))
            logs = o.optJSONArray("logs").mapObjects(Codec::logFromJson)
            draft = Codec.draftFromJson(o.optJSONObject("draft"))
            food = o.optJSONArray("food").mapObjects(Codec::foodFromJson)
            goals = o.optJSONArray("goals").mapObjects(Codec::goalFromJson)
            o.optJSONObject("settings")?.let { settings = Codec.settingsFromJson(it) }
            plannerDraft = o.optString("plannerDraft", "")
            programDraft = o.optString("programDraft", "")
        } catch (_: Exception) {
            // повреждённый файл — сохраним копию, чтобы не потерять данные
            try {
                file.copyTo(File(file.parentFile, "data.broken.json"), overwrite = true)
            } catch (_: Exception) {
            }
        }
    }

    private fun save() {
        if (!loaded) return
        val json = try {
            JSONObject()
                .put("version", 1)
                .put("tasks", tasks.toJsonArray(Codec::taskToJson))
                .put("program", Codec.programToJson(program))
                .put("logs", logs.toJsonArray(Codec::logToJson))
                .put("draft", draft?.let(Codec::draftToJson) ?: JSONObject.NULL)
                .put("food", food.toJsonArray(Codec::foodToJson))
                .put("goals", goals.toJsonArray(Codec::goalToJson))
                .put("settings", Codec.settingsToJson(settings))
                .put("plannerDraft", plannerDraft)
                .put("programDraft", programDraft)
                .toString()
        } catch (_: Exception) {
            return
        }
        val target = file
        io.execute {
            try {
                val tmp = File(target.parentFile, "data.json.tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(target)) {
                    target.writeText(json)
                    tmp.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    // ---------------- Задачи ----------------

    fun addTask(title: String, date: LocalDate, time: LocalTime?) {
        addTasks(listOf(Triple(title, date, time)))
    }

    fun addTasks(items: List<Triple<String, LocalDate, LocalTime?>>) {
        val created = items.filter { it.first.isNotBlank() }
            .map { Task(id = newId(), title = it.first.trim(), date = it.second, time = it.third) }
        if (created.isEmpty()) return
        tasks = tasks + created
        save()
        created.forEach { Reminders.scheduleTask(appContext, it) }
    }

    fun updateTask(t: Task) {
        tasks = tasks.map { if (it.id == t.id) t else it }
        save()
        Reminders.scheduleTask(appContext, t)
    }

    fun toggleTask(id: Long) {
        tasks.find { it.id == id }?.let { updateTask(it.copy(done = !it.done)) }
    }

    fun deleteTask(id: Long) {
        tasks = tasks.filterNot { it.id == id }
        save()
        Reminders.cancelTask(appContext, id)
    }

    /** Невыполненные задачи прошлых дней переезжают на сегодня. */
    fun carryOver() {
        if (!loaded) return
        val today = LocalDate.now()
        var changed = false
        tasks = tasks.map {
            if (!it.done && it.date.isBefore(today)) {
                changed = true
                it.copy(date = today, carried = it.carried + 1)
            } else it
        }
        if (changed) {
            save()
            tasks.forEach { Reminders.scheduleTask(appContext, it) }
        }
    }

    // ---------------- Программа тренировок ----------------

    fun saveExercise(day: Int, ex: Exercise) {
        val list = program[day].orEmpty()
        val newList = if (list.any { it.id == ex.id }) list.map { if (it.id == ex.id) ex else it } else list + ex
        program = program + (day to newList)
        save()
    }

    fun deleteExercise(day: Int, id: Long) {
        program = program + (day to program[day].orEmpty().filterNot { it.id == id })
        save()
    }

    fun moveExercise(day: Int, index: Int, delta: Int) {
        val list = program[day].orEmpty().toMutableList()
        val j = index + delta
        if (index !in list.indices || j !in list.indices) return
        Collections.swap(list, index, j)
        program = program + (day to list.toList())
        save()
    }

    // ---------------- Текущая тренировка ----------------

    fun setsFor(date: LocalDate, ex: Exercise): List<SetEntry> =
        draft?.takeIf { it.date == date }?.sets?.get(ex.id)
            ?: List(ex.sets.coerceAtLeast(1)) { SetEntry() }

    fun updateSet(date: LocalDate, ex: Exercise, index: Int, entry: SetEntry) {
        val cur = draft?.takeIf { it.date == date } ?: WorkoutDraft(date, emptyMap())
        val list = setsFor(date, ex).toMutableList()
        if (index >= list.size) list.add(entry) else list[index] = entry
        draft = cur.copy(sets = cur.sets + (ex.id to list.toList()))
        save()
    }

    fun removeLastSet(date: LocalDate, ex: Exercise) {
        val cur = draft?.takeIf { it.date == date } ?: WorkoutDraft(date, emptyMap())
        val list = setsFor(date, ex)
        if (list.size <= 1) return
        draft = cur.copy(sets = cur.sets + (ex.id to list.dropLast(1)))
        save()
    }

    /** Завершает тренировку. Возвращает null, если ничего не заполнено. */
    fun finishWorkout(date: LocalDate, exercises: List<Exercise>): WorkoutLog? {
        val logged = exercises.map { ex ->
            val sets = setsFor(date, ex)
                .filter { it.reps.isNotBlank() || it.weight.isNotBlank() || it.done }
                .map { if (it.reps.isBlank()) it.copy(reps = ex.reps.toString()) else it }
            ExerciseLog(ex.name, ex.sets, ex.reps, ex.weight, sets)
        }
        if (logged.all { it.sets.isEmpty() }) return null
        val log = WorkoutLog(id = newId(), date = date, exercises = logged)
        logs = listOf(log) + logs
        draft = null
        save()
        generateReport(log.id)
        return log
    }

    fun deleteLog(id: Long) {
        logs = logs.filterNot { it.id == id }
        save()
    }

    fun generateReport(logId: Long) {
        val log = logs.find { it.id == logId } ?: return
        if (logId in reportLoading) return
        reportLoading = reportLoading + logId
        reportErrors = reportErrors - logId
        val previous = logs
            .filter { it.id != logId && it.finishedAt < log.finishedAt }
            .let { older ->
                older.filter { it.date.dayOfWeek == log.date.dayOfWeek }.maxByOrNull { it.finishedAt }
                    ?: older.maxByOrNull { it.finishedAt }
            }
        scope.launch {
            try {
                val text = AiClient.workoutReport(log, previous)
                logs = logs.map { if (it.id == logId) it.copy(report = text) else it }
                save()
            } catch (e: Exception) {
                reportErrors = reportErrors + (logId to (e.message ?: "Не удалось получить отчёт"))
            } finally {
                reportLoading = reportLoading - logId
            }
        }
    }

    // ---------------- Питание ----------------

    fun addFood(date: LocalDate, path: String) {
        food = food + FoodEntry(id = newId(), date = date, time = System.currentTimeMillis(), photoPath = path)
        save()
    }

    fun updateFoodNote(id: Long, note: String) {
        food = food.map { if (it.id == id) it.copy(note = note) else it }
        save()
    }

    fun deleteFood(id: Long) {
        food.find { it.id == id }?.let { entry ->
            try {
                File(entry.photoPath).delete()
            } catch (_: Exception) {
            }
        }
        food = food.filterNot { it.id == id }
        save()
    }

    // ---------------- Цели ----------------

    fun addGoal(title: String, emoji: String) {
        if (title.isBlank()) return
        goals = listOf(Goal(newId(), title.trim(), emoji, System.currentTimeMillis())) + goals
        save()
    }

    fun updateGoal(g: Goal) {
        goals = goals.map { if (it.id == g.id) g else it }
        save()
    }

    fun toggleGoal(id: Long) {
        goals = goals.map {
            if (it.id == id) it.copy(doneAt = if (it.doneAt == null) System.currentTimeMillis() else null) else it
        }
        save()
    }

    fun togglePin(id: Long) {
        goals = goals.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
        save()
    }

    /** Ставит цель [fromId] на место цели [toId]. */
    fun moveGoal(fromId: Long, toId: Long) {
        val list = goals.toMutableList()
        val fi = list.indexOfFirst { it.id == fromId }
        val ti = list.indexOfFirst { it.id == toId }
        if (fi < 0 || ti < 0 || fi == ti) return
        val item = list.removeAt(fi)
        list.add(ti, item)
        goals = list.toList()
        save()
    }

    fun deleteGoal(id: Long) {
        goals = goals.filterNot { it.id == id }
        save()
    }

    // ---------------- Черновики ИИ ----------------

    fun updatePlannerDraft(text: String) {
        plannerDraft = text
        save()
    }

    fun updateProgramDraft(text: String) {
        programDraft = text
        save()
    }

    fun replaceProgram(p: Map<Int, List<Exercise>>) {
        program = p
        save()
    }

    // ---------------- Настройки ----------------

    fun updateSettings(n: AppSettings) {
        val old = settings
        settings = n
        save()
        val strip = { s: AppSettings -> s.copy(baseUrl = "", apiKey = "", model = "", themeMode = "", themeIndex = 0) }
        if (strip(old) != strip(n)) Reminders.scheduleDaily(appContext)
    }

    // ---------------- Темы ----------------

    const val THEME_COUNT = 7

    /** Индекс темы с учётом режима: фиксированная, каждый день новая, каждый заход новая. */
    fun themeIndexFor(date: LocalDate): Int = when (settings.themeMode) {
        "daily" -> Math.floorMod(date.toEpochDay(), THEME_COUNT.toLong()).toInt()
        else -> settings.themeIndex.coerceIn(0, THEME_COUNT - 1)
    }

    /** Вызывается при каждом заходе в приложение. */
    fun rotateLaunchTheme() {
        if (!loaded || settings.themeMode != "launch") return
        updateSettings(settings.copy(themeIndex = (settings.themeIndex + 1) % THEME_COUNT))
    }
}
