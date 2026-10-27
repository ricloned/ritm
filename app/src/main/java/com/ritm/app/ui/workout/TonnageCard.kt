package com.ritm.app.ui.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritm.app.data.ExerciseLog
import com.ritm.app.data.Store
import com.ritm.app.data.WorkoutLog
import java.util.Locale

/** Мышечная группа по названию упражнения (порядок проверок важен). */
fun muscleGroup(name: String): String {
    val n = name.lowercase(Locale.forLanguageTag("ru"))
    fun has(vararg k: String) = k.any { n.contains(it) }
    return when {
        has("бег", "кардио", "растяж", "вело", "дорожк", "ходьб") -> "Кардио"
        has("пресс", "скручив", "планк") -> "Пресс"
        has("трицепс", "брусь", "француз") -> "Трицепс"
        has("бицепс", "молотк", "сгибания рук", "сгибание рук") -> "Бицепс"
        has("дельт", "плеч", "махи", "армейск") -> "Плечи"
        Regex("(^|[^а-яё])ног").containsMatchIn(n) || has("присед", "выпад", "болгар", "икр", "носки", "ягодиц", "румынск", "бедр", "квадр") -> "Ноги"
        has("груд", "лёжа", "лежа", "наклонн", "развод", "сведен", "кроссовер", "бабочк", "отжиман") -> "Грудь"
        has("спин", "тяга", "пуловер", "подтяг", "гиперэкст") -> "Спина"
        else -> "Другое"
    }
}

private val GROUP_COLORS = mapOf(
    "Ноги" to Color(0xFF8E7CF0),
    "Плечи" to Color(0xFFF2A65A),
    "Грудь" to Color(0xFFEF6F8E),
    "Спина" to Color(0xFF3FB6C4),
    "Бицепс" to Color(0xFF5CC97A),
    "Трицепс" to Color(0xFF5B8DEF),
    "Пресс" to Color(0xFFF6C445),
    "Другое" to Color(0xFFB0A8C0),
)

private val GROUP_EMOJI = mapOf(
    "Ноги" to "🦵", "Плечи" to "🏔️", "Грудь" to "🫁", "Спина" to "🪽",
    "Бицепс" to "💪", "Трицепс" to "🔱", "Пресс" to "🧱", "Другое" to "✨",
)

data class Tonnage(
    val total: Double,
    val sets: Int,
    val reps: Int,
    val byGroup: List<Pair<String, Double>>,
)

private fun setKg(weight: String, reps: String): Pair<Double, Int> {
    val w = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
    val r = reps.toIntOrNull() ?: 0
    return (w * r) to r
}

fun tonnageOf(exercises: List<ExerciseLog>): Tonnage {
    val groups = LinkedHashMap<String, Double>()
    var total = 0.0
    var sets = 0
    var reps = 0
    exercises.forEach { e ->
        val g = muscleGroup(e.name)
        if (g == "Кардио") return@forEach
        e.sets.forEach { s ->
            val (kg, r) = setKg(s.weight, s.reps)
            if (r <= 0) return@forEach
            sets++
            reps += r
            total += kg
            if (kg > 0) groups[g] = (groups[g] ?: 0.0) + kg
        }
    }
    return Tonnage(total, sets, reps, groups.entries.sortedByDescending { it.value }.map { it.key to it.value })
}

/** Прошлая тренировка с теми же упражнениями — для сравнения. */
fun previousSimilar(log: WorkoutLog): WorkoutLog? {
    val names = log.exercises.map { it.name.lowercase() }.toSet()
    return Store.logs
        .filter { it.id != log.id && it.finishedAt < log.finishedAt }
        .map { it to it.exercises.count { e -> e.name.lowercase() in names } }
        .filter { it.second >= maxOf(2, names.size / 2) }
        .maxByOrNull { it.first.finishedAt }?.first
}

fun kgText(v: Double): String = String.format(Locale.forLanguageTag("ru"), "%,d", Math.round(v))

@Composable
fun TonnageCard(t: Tonnage, previous: Tonnage?, live: Boolean = false, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val onCard = cs.onPrimary
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
    ) {
        Box {
            // декоративные круги
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 30.dp)
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (live) "ТОННАЖ · ИДЁТ ТРЕНИРОВКА" else "ТОННАЖ ТРЕНИРОВКИ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = onCard.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        kgText(t.total),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = onCard,
                        lineHeight = 46.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "кг",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onCard.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("${t.sets} подходов", onCard)
                    Chip("${t.reps} повторов", onCard)
                    if (previous != null && previous.total > 0 && t.total > 0) {
                        val pct = ((t.total - previous.total) / previous.total * 100).toInt()
                        Chip(
                            (if (pct >= 0) "▲ +$pct%" else "▼ $pct%") + " к прошлой",
                            onCard,
                            strong = true
                        )
                    }
                }
            }
        }
        if (t.byGroup.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.surface)
                    .padding(14.dp)
            ) {
                // сегментированная полоса долей
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50)),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    t.byGroup.forEach { (g, kg) ->
                        Box(
                            Modifier
                                .weight((kg / t.total).toFloat().coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(GROUP_COLORS[g] ?: cs.primary)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val max = t.byGroup.maxOf { it.second }
                t.byGroup.forEach { (g, kg) -> GroupRow(g, kg, kg / t.total, kg / max) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, strong: Boolean = false) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (strong) 0.28f else 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        color = color
    )
}

@Composable
private fun GroupRow(group: String, kg: Double, share: Double, rel: Double) {
    val cs = MaterialTheme.colorScheme
    val color = GROUP_COLORS[group] ?: cs.primary
    val anim by animateFloatAsState(rel.toFloat(), tween(700), label = "bar")
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(GROUP_EMOJI[group] ?: "•", fontSize = 15.sp) }
            Spacer(Modifier.width(10.dp))
            Text(group, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                "${kgText(kg)} кг",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${Math.round(share * 100)}%",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .padding(start = 40.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(cs.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(anim.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
