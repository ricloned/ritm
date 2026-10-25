package com.ritm.app.ai

import com.ritm.app.data.Exercise
import com.ritm.app.data.ExerciseLog
import com.ritm.app.data.Store
import com.ritm.app.data.WorkoutLog
import com.ritm.app.data.toText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Клиент ИИ. Поддерживает любой OpenAI-совместимый API (OpenRouter, OpenAI, DeepSeek, Groq, локальные…)
 * и Anthropic (если адрес содержит anthropic.com).
 */
object AiClient {
    class AiException(message: String) : Exception(message)

    data class PlanItem(
        val title: String,
        val date: LocalDate,
        val time: LocalTime?,
        val selected: Boolean = true,
    )

    private val RU: Locale = Locale.forLanguageTag("ru-RU")

    suspend fun complete(system: String, user: String, maxTokens: Int = 2000): String = withContext(Dispatchers.IO) {
        val s = Store.settings
        if (s.apiKey.isBlank()) throw AiException("Добавь API-ключ в настройках (шестерёнка сверху)")
        if (s.model.isBlank()) throw AiException("Укажи модель в настройках")
        val base = s.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw AiException("Укажи адрес API в настройках")

        val anthropic = base.contains("anthropic.com")
        val endpoint = when {
            anthropic && base.endsWith("/messages") -> base
            anthropic && base.endsWith("/v1") -> "$base/messages"
            anthropic -> "$base/v1/messages"
            base.endsWith("/chat/completions") -> base
            else -> "$base/chat/completions"
        }

        val body = if (anthropic) {
            JSONObject()
                .put("model", s.model.trim())
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        } else {
            JSONObject()
                .put("model", s.model.trim())
                .put("temperature", 0.3)
                .put(
                    "messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user))
                )
        }

        val conn = try {
            URL(endpoint).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("Неверный адрес API: $endpoint")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 180_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            if (anthropic) {
                conn.setRequestProperty("x-api-key", s.apiKey.trim())
                conn.setRequestProperty("anthropic-version", "2023-06-01")
            } else {
                conn.setRequestProperty("Authorization", "Bearer ${s.apiKey.trim()}")
                conn.setRequestProperty("HTTP-Referer", "https://github.com/ritm-app")
                conn.setRequestProperty("X-Title", "Ritm")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw AiException("Ошибка $code: ${extractError(text)}")

            val json = JSONObject(text)
            val out = if (anthropic) {
                val arr = json.optJSONArray("content") ?: JSONArray()
                buildString {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("type") == "text") append(o.optString("text"))
                    }
                }
            } else {
                val msg = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                if (msg.isNull("content")) "" else msg.getString("content")
            }
            val cleaned = out.replace(Regex("(?s)<think>.*?</think>"), "").trim()
            if (cleaned.isEmpty()) throw AiException("Модель вернула пустой ответ")
            cleaned
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException("Нет связи с API: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: JSONException) {
            throw AiException("Не удалось разобрать ответ сервера")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractError(text: String): String {
        return try {
            val o = JSONObject(text)
            val err = o.opt("error")
            when (err) {
                is JSONObject -> err.optString("message").ifBlank { text.take(200) }
                is String -> err
                else -> o.optString("message").ifBlank { text.take(200) }
            }
        } catch (_: Exception) {
            text.take(200).ifBlank { "пустой ответ" }
        }
    }

    // ---------------- Планировщик ----------------

    suspend fun parsePlan(userText: String): List<PlanItem> {
        val today = LocalDate.now()
        val calendar = (0..20).joinToString("\n") { i ->
            val d = today.plusDays(i.toLong())
            "$d — ${d.dayOfWeek.getDisplayName(TextStyle.FULL, RU)}" + if (i == 0) " (сегодня)" else ""
        }
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val system = """
Ты — помощник-планировщик. Пользователь надиктовал или написал свои планы, иногда сумбурно.
Разбери текст на отдельные конкретные задачи и распредели их по датам.
Сейчас $now, сегодня ${today}.
Календарь ближайших дней:
$calendar

Правила:
- Каждая задача — короткая понятная формулировка (до 80 символов), начинай с глагола.
- Если дата не указана — ставь сегодняшнюю. «Завтра», «в пятницу», «через неделю» переводи в конкретную дату по календарю.
- Если указано время — укажи его в формате HH:MM, иначе null. «Утром» = 09:00, «днём» = 13:00, «вечером» = 19:00.
- Если задача повторяется (например «каждый день»), создай отдельные задачи на нужные даты, но не больше чем на 14 дней.
- Не выдумывай задач, которых нет в тексте.

Ответь ТОЛЬКО JSON-массивом без пояснений и без markdown:
[{"title": "Позвонить Диме", "date": "YYYY-MM-DD", "time": "HH:MM"}, {"title": "...", "date": "YYYY-MM-DD", "time": null}]
""".trim()

        val raw = complete(system, userText, maxTokens = 4000)
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) throw AiException("ИИ ответил не списком задач. Попробуй ещё раз.")
        val arr = try {
            JSONArray(raw.substring(start, end + 1))
        } catch (e: JSONException) {
            throw AiException("ИИ вернул некорректный список. Попробуй ещё раз.")
        }
        val timeRegex = Regex("""(\d{1,2})[:.](\d{2})""")
        val items = ArrayList<PlanItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title").trim()
            if (title.isEmpty()) continue
            val date = try {
                LocalDate.parse(o.optString("date").trim().take(10))
            } catch (_: Exception) {
                today
            }
            val timeStr = if (o.isNull("time")) "" else o.optString("time")
            val time = timeRegex.find(timeStr)?.let { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
            }
            items.add(PlanItem(title, if (date.isBefore(today)) today else date, time))
        }
        return items.sortedWith(compareBy<PlanItem>({ it.date }, { it.time ?: LocalTime.MAX }))
    }

    // ---------------- Отчёт о тренировке ----------------

    private fun num(d: Double): String = try {
        BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
    } catch (_: Exception) {
        d.toString()
    }

    fun describeExercise(e: ExerciseLog): String {
        val plan = "план ${e.targetSets}×${e.targetReps}" + if (e.targetWeight > 0) ", ${num(e.targetWeight)} кг" else ""
        val done = if (e.sets.isEmpty()) "пропущено" else e.sets.joinToString(", ") { s ->
            val w = s.weight.replace(',', '.').toDoubleOrNull()
            if (w != null && w > 0) "${s.reps}×${num(w)} кг" else "${s.reps} повт."
        }
        return "${e.name} ($plan): $done"
    }

    suspend fun workoutReport(log: WorkoutLog, previous: WorkoutLog?): String {
        val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", RU)
        val user = buildString {
            appendLine("Сегодняшняя тренировка (${log.date.format(fmt)}), круговая — упражнения идут по кругу:")
            appendLine(log.toText())
            if (previous != null) {
                appendLine()
                appendLine("Прошлая похожая тренировка (${previous.date.format(fmt)}):")
                appendLine(previous.toText())
            }
        }
        val system = """
Ты — дружелюбный персональный тренер. Пользователь тренируется круговым методом: проходит все упражнения подряд,
потом второй круг и т.д. Формат записи: «Упражнение 75кг на 15 (комментарий)».
Напиши КРАТКИЙ отчёт на русском: 3–6 коротких предложений. Отметь, что получилось, как падают повторения от круга к кругу,
сравни с прошлой тренировкой (если есть) — где прогресс в весе или повторениях. Учти комментарии пользователя
(боль, техника, «надо больше»). Дай 1–2 конкретных совета на следующую тренировку (например, где поднять вес).
Пиши обычным текстом, без markdown и заголовков. Можно 1–2 эмодзи.
""".trim()
        return complete(system, user, maxTokens = 800)
    }

    // ---------------- Составление программы ----------------

    data class ProgramResult(val days: Map<Int, List<Exercise>>, val comment: String)

    private fun firstInt(v: Any?, def: Int): Int {
        if (v is Number) return v.toInt()
        val m = Regex("""\d+""").find(v?.toString() ?: "") ?: return def
        return m.value.toIntOrNull() ?: def
    }

    private fun firstDouble(v: Any?): Double {
        if (v is Number) return v.toDouble().takeIf { !it.isNaN() && it >= 0 } ?: 0.0
        val m = Regex("""\d+([.,]\d+)?""").find(v?.toString() ?: "") ?: return 0.0
        return m.value.replace(',', '.').toDoubleOrNull() ?: 0.0
    }

    suspend fun buildProgram(request: String, current: Map<Int, List<Exercise>>): ProgramResult {
        val currentText = (1..7).joinToString("\n") { d ->
            val list = current[d].orEmpty()
            val name = java.time.DayOfWeek.of(d).getDisplayName(TextStyle.FULL, RU)
            "$d ($name): " + if (list.isEmpty()) "отдых" else list.joinToString("; ") {
                "${it.name} ${it.sets}×${it.reps}" + if (it.weight > 0) " ${num(it.weight)} кг" else ""
            }
        }
        val system = """
Ты — опытный персональный тренер. Пользователь рассказывает, как он сейчас тренируется (упражнения, веса, повторения),
какие у него цели, пожелания, ограничения и сколько дней в неделю готов заниматься.
Пользователь тренируется КРУГОВЫМ методом: все упражнения дня подряд, затем повтор круга. sets = количество кругов.
Его записи выглядят так: «Круг 1 / Ноги тренажер 75кг на 15 (комментарий)» — сохраняй его короткие названия упражнений,
если они понятны, и учитывай комментарии («надо больше», «плечо болит»).
Составь на этой основе программу тренировок на неделю. Учитывай текущие рабочие веса пользователя,
ставь реалистичные веса (немного прогрессии), распределяй мышечные группы так, чтобы было время на восстановление.
Дни без тренировок — пустой массив.

Текущая программа пользователя (может быть пустой):
$currentText

Ответь ТОЛЬКО JSON-объектом без markdown и пояснений вокруг:
{"days": {"1": [{"name": "Жим лёжа", "sets": 4, "reps": 8, "weight": 60}], "2": [], "3": [], "4": [], "5": [], "6": [], "7": []},
 "comment": "2–4 предложения: логика программы и советы"}
Ключи 1–7 — дни недели: 1 = понедельник, 7 = воскресенье. weight — вес в кг числом (0, если упражнение без веса).
reps — одно число (для планки — секунды). Названия упражнений на русском.
""".trim()
        val recent = Store.logs.sortedByDescending { it.finishedAt }.take(6)
        val fullRequest = if (recent.isEmpty()) request else buildString {
            appendLine(request)
            appendLine()
            appendLine("Мои последние тренировки (круговые):")
            recent.forEach { appendLine(); appendLine(it.toText()) }
        }
        val raw = complete(system, fullRequest, maxTokens = 4000)
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("ИИ ответил не программой. Попробуй ещё раз.")
        val obj = try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: JSONException) {
            throw AiException("ИИ вернул некорректную программу. Попробуй ещё раз.")
        }
        val daysObj = obj.optJSONObject("days") ?: obj
        val days = HashMap<Int, List<Exercise>>()
        for (d in 1..7) {
            val arr = daysObj.optJSONArray(d.toString()) ?: continue
            val list = ArrayList<Exercise>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                list.add(
                    Exercise(
                        id = Store.newId(),
                        name = name,
                        sets = firstInt(o.opt("sets"), 3).coerceIn(1, 10),
                        reps = firstInt(o.opt("reps"), 10).coerceIn(1, 1000),
                        weight = firstDouble(o.opt("weight")),
                    )
                )
            }
            days[d] = list
        }
        if (days.values.all { it.isEmpty() }) throw AiException("ИИ не смог составить программу. Опиши подробнее.")
        return ProgramResult(days, obj.optString("comment").trim())
    }
}
