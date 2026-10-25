package com.ritm.app.ai

import com.ritm.app.data.ExerciseLog
import com.ritm.app.data.Store
import com.ritm.app.data.WorkoutLog
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
            appendLine("Тренировка ${log.date.format(fmt)}:")
            log.exercises.forEach { appendLine("- " + describeExercise(it)) }
            if (previous != null) {
                appendLine()
                appendLine("Предыдущая тренировка ${previous.date.format(fmt)}:")
                previous.exercises.forEach { appendLine("- " + describeExercise(it)) }
            }
        }
        val system = """
Ты — дружелюбный персональный тренер. Напиши КРАТКИЙ отчёт о тренировке на русском языке: 3–6 коротких предложений.
Отметь, что получилось, сравни с планом и с прошлой тренировкой (если она есть), назови прогресс в весах или повторениях,
отметь пропущенные упражнения и дай 1–2 конкретных совета на следующую тренировку.
Пиши обычным текстом, без markdown и без заголовков. Можно 1–2 эмодзи.
""".trim()
        return complete(system, user, maxTokens = 800)
    }
}
