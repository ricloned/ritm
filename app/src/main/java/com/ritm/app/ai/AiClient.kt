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

    suspend fun complete(system: String, user: String, maxTokens: Int = 2000): String =
        completeChat(system, listOf("user" to user), maxTokens)

    /** Диалог: список пар (role = "user" | "assistant", текст). */
    suspend fun completeChat(
        system: String,
        messages: List<Pair<String, String>>,
        maxTokens: Int = 2000,
        extraUserContent: JSONArray? = null,
    ): String = withContext(Dispatchers.IO) {
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

        val msgArray = JSONArray()
        if (!anthropic) msgArray.put(JSONObject().put("role", "system").put("content", system))
        messages.forEach { (role, text) -> msgArray.put(JSONObject().put("role", role).put("content", text)) }
        extraUserContent?.let { content -> msgArray.put(JSONObject().put("role", "user").put("content", content)) }
        val body = if (anthropic) {
            JSONObject()
                .put("model", s.model.trim())
                .put("max_tokens", maxTokens)
                .put("system", system)
                .put("messages", msgArray)
        } else {
            JSONObject()
                .put("model", s.model.trim())
                .put("temperature", 0.4)
                .put("messages", msgArray)
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

    // ---------------- КБЖУ по фото ----------------

    data class FoodResult(
        val title: String,
        val items: List<com.ritm.app.data.FoodItem>,
    )

    private fun isAnthropic(): Boolean = Store.settings.baseUrl.contains("anthropic.com")

    /** Уменьшает фото до ~1024 px и кодирует в base64 JPEG. */
    private fun encodeImage(path: String): String {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) throw AiException("Не удалось открыть фото")
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 1024) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw AiException("Не удалось открыть фото")
        val scale = 1024f / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else bmp
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }

    suspend fun analyzeFood(photoPath: String, comment: String): FoodResult {
        val hasPhoto = photoPath.isNotBlank() && java.io.File(photoPath).exists()
        val text = buildString {
            append("Посчитай КБЖУ этого приёма пищи.")
            if (comment.isNotBlank()) {
                append("\nМой комментарий (он ГЛАВНЕЕ фото — названия и граммовки бери отсюда): ").append(comment.trim())
            } else {
                append("\nКомментария нет — определи блюда и вес по фото.")
            }
        }
        val system = """
Ты — нутрициолог. Разложи приём пищи на продукты и посчитай КБЖУ каждого.

Правила:
- Комментарий пользователя важнее фото. Если он указал продукты и граммы — используй ровно их.
  Если указал только название блюда — разложи блюдо на типичные ингредиенты.
- Граммовки круп, макарон, риса, гречки, овсянки, бобовых пользователь пишет в СУХОМ виде — считай по сухому продукту,
  если он сам не написал «варёный/готовый». Мясо и рыбу — в сыром виде, если не сказано иное.
- Если граммов нет — оцени вес по фото (размер тарелки, приборов, упаковки). Если на фото этикетка — бери цифры с неё.
- Используй стандартные таблицы пищевой ценности (USDA, таблицы Скурихина, данные производителей) на 100 г и пересчитай на вес.
- Не забывай масло для жарки и соусы, если они явно есть или указаны.

Ответь ТОЛЬКО JSON без markdown:
{"title": "Рис с курицей", "items": [
 {"name": "Рис белый (сухой)", "grams": 100, "kcal": 344, "protein": 6.7, "fat": 0.7, "carbs": 78.9},
 {"name": "Куриное филе (сырое)", "grams": 200, "kcal": 226, "protein": 47.2, "fat": 3.8, "carbs": 0}
]}
Числа без единиц, до 1 знака после запятой. Если это не еда — "items": [].
""".trim()

        suspend fun ask(withPhoto: Boolean): String {
            val content = JSONArray()
            if (withPhoto) {
                val b64 = withContext(Dispatchers.IO) { encodeImage(photoPath) }
                if (isAnthropic()) {
                    content.put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)
                        )
                    )
                    content.put(JSONObject().put("type", "text").put("text", text))
                } else {
                    content.put(JSONObject().put("type", "text").put("text", text))
                    content.put(
                        JSONObject().put("type", "image_url")
                            .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                    )
                }
                return completeChat(system, emptyList(), maxTokens = 1200, extraUserContent = content)
            }
            return complete(system, text, maxTokens = 1200)
        }

        val raw = try {
            ask(hasPhoto)
        } catch (e: AiException) {
            // модель может не уметь в картинки — тогда считаем по комментарию
            if (hasPhoto && comment.isNotBlank()) ask(false)
            else if (hasPhoto) throw AiException("${e.message}\nЕсли модель не понимает фото — напиши состав в комментарии или выбери модель с поддержкой изображений.")
            else throw e
        }
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("ИИ не смог посчитать. Попробуй ещё раз.")
        val o = try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: JSONException) {
            throw AiException("ИИ вернул некорректный ответ. Попробуй ещё раз.")
        }
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<com.ritm.app.data.FoodItem>()
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: continue
            val name = it.optString("name").trim()
            if (name.isEmpty()) continue
            items.add(
                com.ritm.app.data.FoodItem(
                    id = 0,
                    name = name,
                    grams = firstDouble(it.opt("grams")),
                    kcal = firstDouble(it.opt("kcal")),
                    protein = firstDouble(it.opt("protein")),
                    fat = firstDouble(it.opt("fat")),
                    carbs = firstDouble(it.opt("carbs")),
                )
            )
        }
        return FoodResult(o.optString("title").trim().ifBlank { "Еда" }, items)
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

    private fun planText(log: WorkoutLog): String = log.exercises.joinToString("\n") { e ->
        val done = e.sets.size
        val status = when {
            done == 0 -> "не делал"
            done < e.targetSets -> "сделал $done из ${e.targetSets}"
            else -> "выполнено"
        }
        "- ${e.name}: по плану ${e.targetSets} ${com.ritm.app.data.roundsWord(e.targetSets)} — $status"
    }

    private val TRAINER_RULES = """
Пользователь тренируется круговым методом: проходит упражнения подряд, потом следующий круг.
У упражнений РАЗНОЕ число кругов по плану (например, главное упражнение — 4 круга, база — 3, изоляция — 2, кардио — 1).
Поэтому поздние круги короче — это НОРМАЛЬНО и задумано программой. Никогда не называй это «оборвался», «недоделал» или «не хватило сил».
Недоделкой считается только то, что прямо помечено «сделал X из Y» или «не делал».
Формат записи: «Упражнение 75кг на 15 (комментарий)». Цель пользователя — рекомпозиция: мышцы и рост рабочих весов при сжигании жира;
базовые упражнения он делает на 8–9 повторений в первом круге, прибавляет вес, когда во всех кругах выходит 10.
""".trim()

    suspend fun workoutReport(log: WorkoutLog, previous: WorkoutLog?): String {
        val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", RU)
        val user = buildString {
            appendLine("Сегодняшняя тренировка (${log.date.format(fmt)}):")
            appendLine(log.toText())
            appendLine()
            appendLine("План по кругам и выполнение:")
            appendLine(planText(log))
            if (previous != null) {
                appendLine()
                appendLine("Прошлая похожая тренировка (${previous.date.format(fmt)}):")
                appendLine(previous.toText())
            }
        }
        val system = """
Ты — дружелюбный персональный тренер.
$TRAINER_RULES

Напиши КРАТКИЙ отчёт на русском: 3–6 коротких предложений. Отметь, что получилось, как падают повторения от круга к кругу,
сравни с прошлой тренировкой (если есть) — где прогресс в весе или повторениях. Учти комментарии пользователя.
Дай 1–2 конкретных совета на следующую тренировку (где поднять вес, где остаться).
Пиши обычным текстом, без markdown и заголовков. Можно 1–2 эмодзи.
""".trim()
        return complete(system, user, maxTokens = 800)
    }

    /** Ответ тренера в чате по конкретной тренировке. */
    suspend fun trainerChat(log: WorkoutLog, history: List<Pair<String, String>>): String {
        val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", RU)
        val recent = Store.logs.filter { it.id != log.id }.sortedByDescending { it.finishedAt }.take(3)
        val system = buildString {
            appendLine("Ты — опытный персональный тренер, общаешься с подопечным в чате. Отвечай по-русски, по делу, коротко (до 6–8 предложений), без markdown.")
            appendLine("Если подопечный поправляет тебя — признай ошибку просто и спокойно и исправь вывод.")
            appendLine(TRAINER_RULES)
            appendLine()
            appendLine("Обсуждаемая тренировка (${log.date.format(fmt)}):")
            appendLine(log.toText())
            appendLine()
            appendLine("План по кругам:")
            appendLine(planText(log))
            log.report?.let { appendLine(); appendLine("Твой отчёт по ней:"); appendLine(it) }
            appendLine()
            appendLine("Текущая программа:")
            appendLine(com.ritm.app.data.programToText(Store.program))
            if (recent.isNotEmpty()) {
                appendLine()
                appendLine("Другие последние тренировки:")
                recent.forEach { appendLine(); appendLine(it.toText()) }
            }
        }
        return completeChat(system, history, maxTokens = 1000)
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
