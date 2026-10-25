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
    val category: String,
    val createdAt: Long,
    val doneAt: Long? = null,
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
        .put("reps", s.reps).put("weight", s.weight).put("done", s.done)

    fun setFromJson(o: JSONObject): SetEntry = SetEntry(
        reps = o.optString("reps"),
        weight = o.optString("weight"),
        done = o.optBoolean("done"),
    )

    fun logToJson(l: WorkoutLog): JSONObject = JSONObject()
        .put("id", l.id)
        .put("date", l.date.toString())
        .put("report", l.report ?: JSONObject.NULL)
        .put("finishedAt", l.finishedAt)
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
        .put("id", g.id).put("title", g.title).put("category", g.category)
        .put("createdAt", g.createdAt).put("doneAt", g.doneAt ?: JSONObject.NULL)

    fun goalFromJson(o: JSONObject): Goal = Goal(
        id = o.getLong("id"),
        title = o.getString("title"),
        category = o.optString("category", "Другое"),
        createdAt = o.optLong("createdAt"),
        doneAt = if (o.isNull("doneAt")) null else o.optLong("doneAt"),
    )

    fun settingsToJson(s: AppSettings): JSONObject = JSONObject()
        .put("baseUrl", s.baseUrl).put("apiKey", s.apiKey).put("model", s.model)
        .put("digestEnabled", s.digestEnabled).put("digestHour", s.digestHour).put("digestMinute", s.digestMinute)
        .put("workoutReminder", s.workoutReminder).put("workoutHour", s.workoutHour).put("workoutMinute", s.workoutMinute)

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
        )
    }

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
