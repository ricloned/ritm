package com.ritm.app.data

/**
 * Мгновенный расчёт КБЖУ без ИИ: разбираем комментарий «200г творога, теос 250г, 30г джема»
 * и ищем продукты в «Моих продуктах» и встроенном справочнике.
 * Если хоть что-то не распознано однозначно — возвращаем null, и считает ИИ.
 */
object LocalFood {
    private data class Base(
        val name: String,
        val stems: List<String>,
        val kcal: Double, val p: Double, val f: Double, val c: Double,
        /** Жирность, если у продукта есть варианты («5», «9»). null — без вариантов. */
        val pct: String? = null,
        /** Вариант по умолчанию, если в тексте процент не указан. */
        val isDefault: Boolean = true,
    )

    // КБЖУ на 100 г; крупы и макароны — сухие, мясо и рыба — сырые
    private val BASE = listOf(
        Base("Творог 5%", listOf("твор"), 121.0, 17.2, 5.0, 1.8, "5"),
        Base("Творог 0%", listOf("твор"), 71.0, 16.5, 0.0, 1.3, "0", false),
        Base("Творог 2%", listOf("твор"), 103.0, 18.0, 2.0, 3.3, "2", false),
        Base("Творог 9%", listOf("твор"), 159.0, 16.7, 9.0, 2.0, "9", false),
        Base("Рис (сухой)", listOf("рис"), 344.0, 6.7, 0.7, 78.9),
        Base("Гречка (сухая)", listOf("греч"), 343.0, 12.6, 3.3, 62.1),
        Base("Овсяные хлопья (сухие)", listOf("овся", "овсян"), 366.0, 11.9, 7.2, 69.3),
        Base("Макароны (сухие)", listOf("макарон", "спагет", "паста "), 350.0, 12.0, 1.5, 71.0),
        Base("Булгур (сухой)", listOf("булгур"), 342.0, 12.3, 1.3, 57.6),
        Base("Киноа (сухая)", listOf("киноа"), 368.0, 14.1, 6.1, 57.2),
        Base("Куриное филе (сырое)", listOf("куриц", "курин", "грудк"), 113.0, 23.6, 1.9, 0.4),
        Base("Индейка, филе (сырое)", listOf("индей"), 114.0, 24.0, 1.5, 0.0),
        Base("Говядина (сырая)", listOf("говяд"), 187.0, 18.9, 12.4, 0.0),
        Base("Свинина (сырая)", listOf("свинин"), 242.0, 16.0, 21.0, 0.0),
        Base("Лосось (сырой)", listOf("лосос", "семг", "сёмг"), 208.0, 20.0, 13.0, 0.0),
        Base("Тунец консервированный", listOf("тунец", "тунц"), 96.0, 21.0, 1.0, 0.0),
        Base("Яйцо куриное", listOf("яйц", "яиц"), 157.0, 12.7, 11.5, 0.7),
        Base("Молоко 2,5%", listOf("молок"), 52.0, 2.8, 2.5, 4.7, "2.5"),
        Base("Молоко 3,2%", listOf("молок"), 60.0, 2.9, 3.2, 4.7, "3.2", false),
        Base("Кефир 1%", listOf("кефир"), 40.0, 3.0, 1.0, 4.0, "1"),
        Base("Кефир 2,5%", listOf("кефир"), 53.0, 2.9, 2.5, 4.0, "2.5", false),
        Base("Банан", listOf("банан"), 96.0, 1.5, 0.2, 21.8),
        Base("Яблоко", listOf("яблок"), 47.0, 0.4, 0.4, 9.8),
        Base("Хлеб", listOf("хлеб"), 242.0, 8.0, 3.0, 48.0),
        Base("Масло растительное", listOf("оливков", "подсолн", "растит"), 899.0, 0.0, 99.9, 0.0),
        Base("Масло сливочное", listOf("сливочн"), 748.0, 0.5, 82.5, 0.8),
        Base("Арахисовая паста", listOf("арахис"), 588.0, 25.0, 50.0, 20.0),
        Base("Джем", listOf("джем", "варень", "конфитюр"), 263.0, 0.3, 0.1, 65.0),
        Base("Мёд", listOf("мёд", "меда", "мёда", "мед "), 304.0, 0.3, 0.0, 82.0),
        Base("Сахар", listOf("сахар"), 398.0, 0.0, 0.0, 99.7),
        Base("Протеин (сывороточный)", listOf("протеин", "whey"), 380.0, 75.0, 6.0, 8.0),
        Base("Картофель (сырой)", listOf("картоф", "картош"), 77.0, 2.0, 0.4, 16.3),
        Base("Огурец", listOf("огур"), 15.0, 0.8, 0.1, 2.8),
        Base("Помидор", listOf("помид", "томат"), 20.0, 1.1, 0.2, 3.7),
    )

    private val gramsRe = Regex("""(\d+(?:[.,]\d+)?)\s*(кг|гр|грамм[а-я]*|г|мл|g)(?![а-я])""")
    private val pctRe = Regex("""(\d+(?:[.,]\d+)?)\s*%""")
    private val eggRe = Regex("""(\d+)\s*(?:шт\.?\s*)?(?:яйц|яиц)|(?:яйц|яиц)[а-я]*\s*(\d+)\s*(?:шт)?""")

    data class Result(val title: String, val items: List<FoodItem>)

    private fun norm(s: String) = " " + s.lowercase().replace('ё', 'е') + " "

    private fun stemOf(word: String): String {
        val w = word.lowercase().replace('ё', 'е').filter { it.isLetter() }
        return if (w.length <= 4) w else w.take(maxOf(4, w.length - 2))
    }

    fun tryLocal(comment: String, myFoods: List<MyFood>): Result? {
        if (comment.isBlank()) return null
        val parts = comment.split(Regex("""[,;\n+]|\sи\s|\sс\s""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val items = ArrayList<FoodItem>()
        for (raw in parts) {
            val text = norm(raw)
            // граммы
            var grams: Double? = null
            gramsRe.find(text)?.let { m ->
                val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (v != null) grams = if (m.groupValues[2] == "кг") v * 1000 else v
            }
            if (grams == null) {
                eggRe.find(text)?.let { m ->
                    val n = (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull()
                    if (n != null) grams = n * 55.0
                }
            }
            val g = grams ?: return null
            val pct = pctRe.find(text)?.groupValues?.get(1)?.replace(',', '.')

            // 1) «Мои продукты» — приоритет
            val mine = myFoods.filter { f ->
                val first = f.name.split(Regex("""[\s(]""")).firstOrNull { it.length >= 3 } ?: return@filter false
                val st = stemOf(first)
                st.length >= 3 && text.contains(st) &&
                    (pct == null || f.name.replace(',', '.').contains("$pct%"))
            }
            if (mine.size == 1 || (mine.size > 1 && mine.map { it.name }.distinct().size == 1)) {
                val f = mine.first()
                items.add(FoodItem(0, f.name, g, f.kcal * g / 100, f.protein * g / 100, f.fat * g / 100, f.carbs * g / 100))
                continue
            }
            if (mine.size > 1) return null

            // 2) встроенный справочник
            val byStem = BASE.filter { b -> b.stems.any { text.contains(it.lowercase().replace('ё', 'е')) } }
            if (byStem.isEmpty()) return null
            val groups = byStem.map { it.stems.first() }.distinct()
            if (groups.size != 1) return null // неоднозначно (например «сыр» и «сырой»)
            val pick = when {
                pct != null -> byStem.firstOrNull { it.pct == pct } ?: return null
                else -> byStem.firstOrNull { it.isDefault } ?: return null
            }
            items.add(FoodItem(0, pick.name, g, pick.kcal * g / 100, pick.p * g / 100, pick.f * g / 100, pick.c * g / 100))
        }
        if (items.isEmpty()) return null
        val title = comment.trim().replaceFirstChar { it.uppercase() }.take(60)
        return Result(title, items.map { it.copy(
            kcal = Math.round(it.kcal * 10) / 10.0,
            protein = Math.round(it.protein * 10) / 10.0,
            fat = Math.round(it.fat * 10) / 10.0,
            carbs = Math.round(it.carbs * 10) / 10.0,
        ) })
    }
}
