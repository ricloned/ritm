package com.ritm.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritm.app.ai.AiJobs
import com.ritm.app.data.Store
import com.ritm.app.ui.AiProgressLine
import com.ritm.app.ui.UiState
import com.ritm.app.ui.appendDictated
import com.ritm.app.ui.rememberDictation
import java.time.LocalDate

private val BURN_A = Color(0xFFFF8A3D)
private val BURN_B = Color(0xFFE5637A)
private val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private fun kcal(v: Int): String = String.format(java.util.Locale.forLanguageTag("ru"), "%,d", v)

/** Плашка «сколько сжигаешь за день» вверху питания. */
@Composable
fun BurnCard(date: LocalDate, eaten: Double) {
    val s = Store.settings
    val loading = UiState.burnLoading
    var editing by remember { mutableStateOf(false) }
    val days = s.burnByDay

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(BURN_A, BURN_B)))
            .clickable { editing = true }
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 34.dp, y = (-34).dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Column(Modifier.padding(16.dp)) {
            if (days.size != 7) {
                Text("🔥 Сколько ты сжигаешь за день?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (loading) "Считаю…" else "Опиши свою неделю — ИИ посчитает расход калорий на каждый день. Нажми сюда.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
                if (loading) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                }
                return@Column
            }
            val idx = date.dayOfWeek.value - 1
            val burn = days[idx]
            val diff = eaten.toInt() - burn
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "🔥 РАСХОД · ${DAY_SHORT[idx].uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("≈ ${kcal(burn)}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(" ккал", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            }
            if (eaten > 0) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("съедено ${kcal(eaten.toInt())}")
                    Pill(
                        if (diff <= 0) "дефицит ${kcal(-diff)}" else "профицит ${kcal(diff)}",
                        strong = true
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // мини-график по дням недели
            val max = days.max().toFloat()
            val min = (days.min() * 0.85f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { i, v ->
                    val frac = ((v - min) / (max - min).coerceAtLeast(1f)).coerceIn(0.12f, 1f)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((38 * frac).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = if (i == idx) 1f else 0.35f))
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            DAY_SHORT[i],
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = if (i == idx) 1f else 0.75f),
                            fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Базовый обмен ${kcal(s.burnBmr)} · в среднем ${kcal(days.average().toInt())} ккал/день",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }

    if (editing) BurnDialog(onDismiss = { editing = false })
}

@Composable
private fun Pill(text: String, strong: Boolean = false) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (strong) 0.30f else 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        color = Color.White
    )
}

@Composable
private fun BurnDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = Store.settings
    var text by remember { mutableStateOf(s.weekDescription) }
    val dictate = rememberDictation("Как проходит твоя неделя?") { r -> text = appendDictated(text, r) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Расход калорий") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Опиши обычную неделю: работа (сидячая/на ногах), сколько ходишь, дорога, тренировки, выходные. " +
                        "Пол, возраст, рост, вес и программу тренировок ИИ возьмёт из приложения сам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    placeholder = {
                        Text("Например: работаю за компом 5/2, до работы 20 мин пешком, ~7–8 тыс. шагов. Пн, Вт, Чт, Пт — зал ~1 час. Сб, Вс — отжимания, подтягивания, бег 15 мин, скакалка.")
                    },
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = { IconButton(onClick = dictate) { Icon(Icons.Rounded.Mic, "Надиктовать") } },
                )
                if (UiState.burnLoading) AiProgressLine("burn")
                UiState.burnError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
                if (s.burnComment.isNotBlank() && !UiState.burnLoading) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.primaryContainer)
                            .padding(12.dp)
                    ) {
                        Text("💬 Как посчитано", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(s.burnComment, style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Store.updateSettings(Store.settings.copy(weekDescription = text.trim()))
                    AiJobs.startBurn()
                },
                enabled = text.isNotBlank() && !UiState.burnLoading
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (s.burnByDay.size == 7) "Пересчитать" else "Рассчитать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
