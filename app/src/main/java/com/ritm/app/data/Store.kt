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
    var checks by mutableStateOf(listOf<BodyCheck>())
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
    var chatLoading by mutableStateOf(setOf<Long>())
        private set
    var chatErrors by mutableStateOf(mapOf<Long, String>())
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
            checks = o.optJSONArray("checks").mapObjects(Codec::checkFromJson)
            o.optJSONObject("settings")?.let { settings = Codec.settingsFromJson(it) }
            if (logs.any { it.dayNumber == 0 }) {
                val ordered = logs.sortedBy { it.finishedAt }
                var n = 0
                val numbers = HashMap<Long, Int>()
                ordered.forEach { l -> n = if (l.dayNumber > 0) l.dayNumber else n + 1; numbers[l.id] = n }
                logs = logs.map { it.copy(dayNumber = numbers[it.id] ?: it.dayNumber) }
            }
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
                .put("checks", checks.toJsonArray(Codec::checkToJson))
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

    // ---------------- Текущая тренировка (круговая) ----------------

    fun nextDayNumber(): Int = (logs.maxOfOrNull { it.dayNumber } ?: 0) + 1

    fun setsFor(date: LocalDate, ex: Exercise): List<SetEntry> =
        draft?.takeIf { it.date == date }?.sets?.get(ex.id)
            ?: List(ex.sets.coerceAtLeast(1)) { SetEntry() }

    /** Подсказка для круга: что было в прошлый раз в этом упражнении, иначе цель из программы. */
    fun hint(ex: Exercise, round: Int): Pair<String, String> {
        val prev = logs.asSequence()
            .sortedByDescending { it.finishedAt }
            .mapNotNull { l -> l.exercises.firstOrNull { it.name.equals(ex.name, ignoreCase = true) && it.sets.isNotEmpty() } }
            .firstOrNull()
        val set = prev?.sets?.getOrNull(round) ?: prev?.sets?.lastOrNull()
        val w = set?.weight?.takeIf { it.isNotBlank() } ?: if (ex.weight > 0) numText(ex.weight) else ""
        val r = set?.reps?.takeIf { it.isNotBlank() } ?: ex.reps.toString()
        return w to r
    }

    private fun putSets(date: LocalDate, ex: Exercise, list: List<SetEntry>) {
        val cur = draft?.takeIf { it.date == date } ?: WorkoutDraft(date, emptyMap())
        draft = cur.copy(sets = cur.sets + (ex.id to list))
        save()
    }

    fun updateSet(date: LocalDate, ex: Exercise, index: Int, entry: SetEntry) {
        val list = setsFor(date, ex).toMutableList()
        while (list.size <= index) list.add(SetEntry())
        list[index] = entry
        putSets(date, ex, list.toList())
    }

    /** Вес переносится на следующие круги, если там пусто или стоял прежний вес. */
    fun setWeight(date: LocalDate, ex: Exercise, index: Int, weight: String) {
        val list = setsFor(date, ex).toMutableList()
        while (list.size <= index) list.add(SetEntry())
        val old = list[index].weight
        list[index] = list[index].copy(weight = weight)
        for (j in index + 1 until list.size) {
            if (list[j].weight.isBlank() || list[j].weight == old) list[j] = list[j].copy(weight = weight)
        }
        putSets(date, ex, list.toList())
    }

    fun addRound(date: LocalDate, exercises: List<Exercise>) {
        val rounds = exercises.maxOfOrNull { setsFor(date, it).size } ?: 0
        exercises.forEach { ex ->
            val list = setsFor(date, ex).toMutableList()
            if (list.size == rounds) {
                val last = list.lastOrNull()
                list.add(SetEntry(weight = last?.weight ?: ""))
                putSets(date, ex, list.toList())
            }
        }
    }

    fun removeRound(date: LocalDate, exercises: List<Exercise>) {
        val rounds = exercises.maxOfOrNull { setsFor(date, it).size } ?: 0
        if (rounds <= 1) return
        exercises.forEach { ex ->
            val list = setsFor(date, ex)
            if (list.size == rounds) putSets(date, ex, list.dropLast(1))
        }
    }

    /** Завершает тренировку. Возвращает null, если ничего не заполнено. */
    fun finishWorkout(date: LocalDate, exercises: List<Exercise>): WorkoutLog? {
        val logged = exercises.map { ex ->
            val sets = setsFor(date, ex).mapIndexedNotNull { i, s ->
                if (s.reps.isBlank() && s.weight.isBlank() && !s.done) return@mapIndexedNotNull null
                val (hw, hr) = hint(ex, i)
                s.copy(
                    reps = s.reps.ifBlank { hr },
                    weight = if (s.weight.isBlank() && s.done) hw else s.weight,
                    done = true,
                )
            }
            ExerciseLog(ex.name, ex.sets, ex.reps, ex.weight, sets)
        }
        if (logged.all { it.sets.isEmpty() }) return null
        val log = WorkoutLog(id = newId(), date = date, exercises = logged, dayNumber = nextDayNumber())
        logs = listOf(log) + logs
        draft = null
        save()
        generateReport(log.id)
        return log
    }

    fun updateLogDate(id: Long, date: LocalDate) {
        logs = logs.map { if (it.id == id) it.copy(date = date) else it }
        save()
    }

    /**
     * Импорт прошлых тренировок из текста:
     * «День 8 / Круг 1 / Ноги тренажер 75кг на 15 (коммент)».
     */
    fun importLogs(text: String, lastDate: LocalDate): Int {
        val dayRe = Regex("""^\s*день\s*(\d+)""", RegexOption.IGNORE_CASE)
        val roundRe = Regex("""^\s*круг\s*(\d+)""", RegexOption.IGNORE_CASE)
        val lineRe = Regex("""^\s*(.+?)\s+(?:(\d+(?:[.,]\d+)?)\s*кг\s*)?на\s+(\d+)\s*(?:\((.*)\))?\s*$""", RegexOption.IGNORE_CASE)

        data class Day(val number: Int, val order: LinkedHashMap<String, MutableList<SetEntry>> = LinkedHashMap())
        val days = ArrayList<Day>()
        var cur: Day? = null
        var round = 0
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            dayRe.find(line)?.let { m ->
                cur = Day(m.groupValues[1].toInt()).also { days.add(it) }
                round = 0
                return@forEach
            }
            roundRe.find(line)?.let { m ->
                round = m.groupValues[1].toInt() - 1
                return@forEach
            }
            val m = lineRe.find(line) ?: return@forEach
            val day = cur ?: Day(0).also { cur = it; days.add(it) }
            val name = m.groupValues[1].trim()
            val list = day.order.getOrPut(name) { mutableListOf() }
            val entry = SetEntry(
                reps = m.groupValues[3],
                weight = m.groupValues[2].replace(',', '.'),
                done = true,
                comment = m.groupValues[4].trim(),
            )
            if (round < list.size) list.add(entry) else {
                while (list.size < round) list.add(SetEntry(done = true))
                list.add(entry)
            }
        }
        val valid = days.filter { it.order.isNotEmpty() }
        if (valid.isEmpty()) return 0
        val maxNumber = valid.maxOf { it.number }
        val base = System.currentTimeMillis() - 365L * 24 * 3600 * 1000
        val newLogs = valid.map { d ->
            val n = if (d.number > 0) d.number else maxNumber + 1
            WorkoutLog(
                id = newId(),
                date = lastDate.minusDays((maxNumber - n).toLong().coerceAtLeast(0) * 2),
                exercises = d.order.map { (name, sets) ->
                    val w = sets.firstOrNull()?.weight?.toDoubleOrNull() ?: 0.0
                    val r = sets.firstOrNull()?.reps?.toIntOrNull() ?: 10
                    ExerciseLog(name, sets.size, r, w, sets)
                },
                finishedAt = base + n * 60_000L,
                dayNumber = n,
            )
        }
        logs = (logs + newLogs).sortedWith(compareByDescending<WorkoutLog> { it.dayNumber }.thenByDescending { it.finishedAt })
        save()
        return newLogs.size
    }

    fun sendChat(logId: Long, text: String) {
        val log = logs.find { it.id == logId } ?: return
        if (text.isBlank() || logId in chatLoading) return
        val withUser = log.chat + ChatMsg("user", text.trim())
        logs = logs.map { if (it.id == logId) it.copy(chat = withUser) else it }
        save()
        chatLoading = chatLoading + logId
        chatErrors = chatErrors - logId
        scope.launch {
            try {
                val current = logs.find { it.id == logId } ?: return@launch
                val history = withUser.takeLast(20).map { it.role to it.text }
                val answer = AiClient.trainerChat(current, history)
                logs = logs.map { if (it.id == logId) it.copy(chat = it.chat + ChatMsg("assistant", answer)) else it }
                save()
            } catch (e: Exception) {
                chatErrors = chatErrors + (logId to (e.message ?: "Не удалось получить ответ"))
            } finally {
                chatLoading = chatLoading - logId
            }
        }
    }

    fun clearChat(logId: Long) {
        logs = logs.map { if (it.id == logId) it.copy(chat = emptyList()) else it }
        save()
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

    // ---------------- Прогресс (еженедельные замеры) ----------------

    fun saveCheck(c: BodyCheck) {
        val old = checks.find { it.id == c.id }
        old?.photos?.filter { it !in c.photos }?.forEach { deleteFile(it) }
        checks = (if (old != null) checks.map { if (it.id == c.id) c else it } else checks + c)
            .sortedBy { it.date }
        save()
    }

    fun deleteCheck(id: Long) {
        checks.find { it.id == id }?.photos?.forEach { deleteFile(it) }
        checks = checks.filterNot { it.id == id }
        save()
    }

    fun deleteFile(path: String) {
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
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
