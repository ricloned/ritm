package com.ritm.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

data class Task(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val carried: Int = 0,
)

/** Упражнение в программе: цель на день. */
data class Exercise(
    val id: Long,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
)

data class SetEntry(
    val reps: String = "",
    val weight: String = "",
    val done: Boolean = false,
    val comment: String = "",
)

data class ExerciseLog(
    val name: String,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeight: Double,
    val sets: List<SetEntry>,
)

data class WorkoutLog(
    val id: Long,
    val date: LocalDate,
    val exercises: List<ExerciseLog>,
    val report: String? = null,
    val finishedAt: Long = System.currentTimeMillis(),
    val dayNumber: Int = 0,
)

data class WorkoutDraft(
    val date: LocalDate,
    val sets: Map<Long, List<SetEntry>>,
)

data class FoodEntry(
    val id: Long,
    val date: LocalDate,
    val time: Long,
    val photoPath: String,
    val note: String = "",
)

data class Goal(
    val id: Long,
    val title: String,
    val emoji: String,
    val createdAt: Long,
    val doneAt: Long? = null,
    val pinned: Boolean = false,
)

/** Еженедельный замер: вес, талия, фото. */
data class BodyCheck(
    val id: Long,
    val date: LocalDate,
    val weight: Double?,
    val waist: Double?,
    val photos: List<String>,
    val note: String = "",
)

data class AppSettings(
    val baseUrl: String = "https://openrouter.ai/api/v1",
    val apiKey: String = "",
    val model: String = "openai/gpt-4o-mini",
    val digestEnabled: Boolean = true,
    val digestHour: Int = 9,
    val digestMinute: Int = 0,
    val workoutReminder: Boolean = false,
    val workoutHour: Int = 18,
    val workoutMinute: Int = 0,
    val foodReminders: Boolean = true,
    val foodTimes: List<Int> = listOf(9 * 60, 14 * 60, 19 * 60),
    val weeklyReminder: Boolean = true,
    val weeklyDay: Int = 7,
    val weeklyHour: Int = 10,
    val weeklyMinute: Int = 0,
    val themeMode: String = "fixed",
    val themeIndex: Int = 0,
)

// ---------- JSON ----------

internal fun JSONObject.strOrNull(key: String): String? = if (isNull(key)) null else optString(key)

internal fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        try {
            out.add(f(o))
        } catch (_: Exception) {
        }
    }
    return out
}

internal fun <T> List<T>.toJsonArray(f: (T) -> JSONObject): JSONArray {
    val a = JSONArray()
    forEach { a.put(f(it)) }
    return a
}

internal object Codec {
    fun taskToJson(t: Task): JSONObject = JSONObject()
        .put("id", t.id)
        .put("title", t.title)
        .put("date", t.date.toString())
        .put("time", t.time?.toString() ?: JSONObject.NULL)
        .put("done", t.done)
        .put("createdAt", t.createdAt)
        .put("carried", t.carried)

    fun taskFromJson(o: JSONObject): Task = Task(
        id = o.getLong("id"),
        title = o.getString("title"),
        date = LocalDate.parse(o.getString("date")),
        time = o.strOrNull("time")?.let { LocalTime.parse(it) },
        done = o.optBoolean("done"),
        createdAt = o.optLong("createdAt"),
        carried = o.optInt("carried"),
    )

    fun exerciseToJson(e: Exercise): JSONObject = JSONObject()
        .put("id", e.id).put("name", e.name).put("sets", e.sets).put("reps", e.reps).put("weight", e.weight)

    fun exerciseFromJson(o: JSONObject): Exercise = Exercise(
        id = o.getLong("id"),
        name = o.getString("name"),
        sets = o.optInt("sets", 3),
        reps = o.optInt("reps", 10),
        weight = o.optDouble("weight", 0.0),
    )

    fun setToJson(s: SetEntry): JSONObject = JSONObject()
        .put("reps", s.reps).put("weight", s.weight).put("done", s.done).put("comment", s.comment)

    fun setFromJson(o: JSONObject): SetEntry = SetEntry(
        reps = o.optString("reps"),
        weight = o.optString("weight"),
        done = o.optBoolean("done"),
        comment = o.optString("comment"),
    )

    fun logToJson(l: WorkoutLog): JSONObject = JSONObject()
        .put("id", l.id)
        .put("date", l.date.toString())
        .put("report", l.report ?: JSONObject.NULL)
        .put("finishedAt", l.finishedAt)
        .put("dayNumber", l.dayNumber)
        .put("exercises", l.exercises.toJsonArray { e ->
            JSONObject()
                .put("name", e.name)
                .put("targetSets", e.targetSets)
                .put("targetReps", e.targetReps)
                .put("targetWeight", e.targetWeight)
                .put("sets", e.sets.toJsonArray(::setToJson))
        })

    fun logFromJson(o: JSONObject): WorkoutLog = WorkoutLog(
        id = o.getLong("id"),
        date = LocalDate.parse(o.getString("date")),
        report = o.strOrNull("report"),
        finishedAt = o.optLong("finishedAt"),
        dayNumber = o.optInt("dayNumber", 0),
        exercises = o.optJSONArray("exercises").mapObjects { e ->
            ExerciseLog(
                name = e.getString("name"),
                targetSets = e.optInt("targetSets"),
                targetReps = e.optInt("targetReps"),
                targetWeight = e.optDouble("targetWeight", 0.0),
                sets = e.optJSONArray("sets").mapObjects(::setFromJson),
            )
        },
    )

    fun foodToJson(f: FoodEntry): JSONObject = JSONObject()
        .put("id", f.id).put("date", f.date.toString()).put("time", f.time)
        .put("photoPath", f.photoPath).put("note", f.note)

    fun foodFromJson(o: JSONObject): FoodEntry = FoodEntry(
        id = o.getLong("id"),
        date = LocalDate.parse(o.getString("date")),
        time = o.optLong("time"),
        photoPath = o.getString("photoPath"),
        note = o.optString("note"),
    )

    fun goalToJson(g: Goal): JSONObject = JSONObject()
        .put("id", g.id).put("title", g.title).put("emoji", g.emoji)
        .put("createdAt", g.createdAt).put("doneAt", g.doneAt ?: JSONObject.NULL)
        .put("pinned", g.pinned)

    private fun legacyEmoji(category: String): String = when (category) {
        "Спорт" -> "🏃"
        "Упражнения" -> "💪"
        "Финансы" -> "💰"
        else -> "🎯"
    }

    fun goalFromJson(o: JSONObject): Goal = Goal(
        id = o.getLong("id"),
        title = o.getString("title"),
        emoji = o.strOrNull("emoji")?.takeIf { it.isNotBlank() } ?: legacyEmoji(o.optString("category")),
        createdAt = o.optLong("createdAt"),
        doneAt = if (o.isNull("doneAt")) null else o.optLong("doneAt"),
        pinned = o.optBoolean("pinned"),
    )

    fun settingsToJson(s: AppSettings): JSONObject = JSONObject()
        .put("baseUrl", s.baseUrl).put("apiKey", s.apiKey).put("model", s.model)
        .put("digestEnabled", s.digestEnabled).put("digestHour", s.digestHour).put("digestMinute", s.digestMinute)
        .put("workoutReminder", s.workoutReminder).put("workoutHour", s.workoutHour).put("workoutMinute", s.workoutMinute)
        .put("foodReminders", s.foodReminders)
        .put("foodTimes", JSONArray().also { a -> s.foodTimes.forEach { a.put(it) } })
        .put("themeMode", s.themeMode).put("themeIndex", s.themeIndex)
        .put("weeklyReminder", s.weeklyReminder).put("weeklyDay", s.weeklyDay)
        .put("weeklyHour", s.weeklyHour).put("weeklyMinute", s.weeklyMinute)

    fun settingsFromJson(o: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            baseUrl = o.optString("baseUrl", d.baseUrl),
            apiKey = o.optString("apiKey", d.apiKey),
            model = o.optString("model", d.model),
            digestEnabled = o.optBoolean("digestEnabled", d.digestEnabled),
            digestHour = o.optInt("digestHour", d.digestHour),
            digestMinute = o.optInt("digestMinute", d.digestMinute),
            workoutReminder = o.optBoolean("workoutReminder", d.workoutReminder),
            workoutHour = o.optInt("workoutHour", d.workoutHour),
            workoutMinute = o.optInt("workoutMinute", d.workoutMinute),
            foodReminders = o.optBoolean("foodReminders", d.foodReminders),
            foodTimes = o.optJSONArray("foodTimes")?.let { a ->
                (0 until a.length()).map { a.optInt(it) }.filter { it in 0 until 24 * 60 }.sorted()
            } ?: d.foodTimes,
            weeklyReminder = o.optBoolean("weeklyReminder", d.weeklyReminder),
            weeklyDay = o.optInt("weeklyDay", d.weeklyDay).coerceIn(1, 7),
            weeklyHour = o.optInt("weeklyHour", d.weeklyHour),
            weeklyMinute = o.optInt("weeklyMinute", d.weeklyMinute),
            themeMode = o.optString("themeMode", d.themeMode),
            themeIndex = o.optInt("themeIndex", d.themeIndex),
        )
    }

    fun checkToJson(c: BodyCheck): JSONObject = JSONObject()
        .put("id", c.id)
        .put("date", c.date.toString())
        .put("weight", c.weight ?: JSONObject.NULL)
        .put("waist", c.waist ?: JSONObject.NULL)
        .put("photos", JSONArray().also { a -> c.photos.forEach { a.put(it) } })
        .put("note", c.note)

    fun checkFromJson(o: JSONObject): BodyCheck = BodyCheck(
        id = o.getLong("id"),
        date = LocalDate.parse(o.getString("date")),
        weight = if (o.isNull("weight")) null else o.optDouble("weight").takeIf { !it.isNaN() },
        waist = if (o.isNull("waist")) null else o.optDouble("waist").takeIf { !it.isNaN() },
        photos = o.optJSONArray("photos")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }
            ?: emptyList(),
        note = o.optString("note"),
    )

    fun programToJson(p: Map<Int, List<Exercise>>): JSONObject {
        val o = JSONObject()
        p.forEach { (day, list) -> o.put(day.toString(), list.toJsonArray(::exerciseToJson)) }
        return o
    }

    fun programFromJson(o: JSONObject?): Map<Int, List<Exercise>> {
        if (o == null) return emptyMap()
        val out = HashMap<Int, List<Exercise>>()
        o.keys().forEach { k ->
            val day = k.toIntOrNull() ?: return@forEach
            out[day] = o.optJSONArray(k).mapObjects(::exerciseFromJson)
        }
        return out
    }

    fun draftToJson(d: WorkoutDraft): JSONObject {
        val sets = JSONObject()
        d.sets.forEach { (id, list) -> sets.put(id.toString(), list.toJsonArray(::setToJson)) }
        return JSONObject().put("date", d.date.toString()).put("sets", sets)
    }

    fun draftFromJson(o: JSONObject?): WorkoutDraft? {
        if (o == null) return null
        return try {
            val setsObj = o.optJSONObject("sets") ?: JSONObject()
            val map = HashMap<Long, List<SetEntry>>()
            setsObj.keys().forEach { k ->
                val id = k.toLongOrNull() ?: return@forEach
                map[id] = setsObj.optJSONArray(k).mapObjects(::setFromJson)
            }
            WorkoutDraft(LocalDate.parse(o.getString("date")), map)
        } catch (_: Exception) {
            null
        }
    }
}

// ---------- Текстовый формат тренировки ----------

internal fun numText(d: Double): String = try {
    java.math.BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
} catch (_: Exception) {
    d.toString()
}

/** «75кг на 15 (коммент)» или «на 12». */
fun SetEntry.lineText(): String {
    val w = weight.replace(',', '.').toDoubleOrNull()
    val base = if (w != null && w > 0) "${numText(w)}кг на $reps" else "на $reps"
    return if (comment.isNotBlank()) "$base (${comment.trim()})" else base
}

/** Тренировка в формате «День N / Круг 1 / Упражнение 75кг на 15». */
fun WorkoutLog.toText(withHeader: Boolean = true): String = buildString {
    if (withHeader) appendLine(if (dayNumber > 0) "День $dayNumber" else "Тренировка")
    val rounds = exercises.maxOfOrNull { it.sets.size } ?: 0
    for (r in 0 until rounds) {
        if (r > 0) appendLine()
        appendLine("Круг ${r + 1}")
        exercises.forEach { e ->
            e.sets.getOrNull(r)?.let { appendLine("${e.name} ${it.lineText()}") }
        }
    }
    val skipped = exercises.filter { it.sets.isEmpty() }
    if (skipped.isNotEmpty()) {
        appendLine()
        appendLine("Пропущено: " + skipped.joinToString(", ") { it.name })
    }
}.trimEnd()

fun roundsWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "круг"
        m10 in 2..4 && m100 !in 12..14 -> "круга"
        else -> "кругов"
    }
}

/** «90кг на 12 · 4 круга» */
fun Exercise.planText(): String =
    (if (weight > 0) "${numText(weight)}кг на $reps" else "на $reps") + " · $sets ${roundsWord(sets)}"

/** Вся программа недели текстом — чтобы скопировать и отправить. */
fun programToText(program: Map<Int, List<Exercise>>): String = buildString {
    appendLine("Моя программа (круговая тренировка)")
    for (d in 1..7) {
        val list = program[d].orEmpty()
        val dayName = java.time.DayOfWeek.of(d)
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("ru-RU"))
            .replaceFirstChar { it.titlecase() }
        appendLine()
        if (list.isEmpty()) {
            appendLine("$dayName — отдых")
        } else {
            val rounds = list.maxOf { it.sets }
            appendLine("$dayName · $rounds ${roundsWord(rounds)}")
            list.forEach { e ->
                val base = if (e.weight > 0) "${numText(e.weight)}кг на ${e.reps}" else "на ${e.reps}"
                val extra = if (e.sets != rounds) " (${e.sets} ${roundsWord(e.sets)})" else ""
                appendLine("${e.name} $base$extra")
            }
        }
    }
}.trimEnd()
