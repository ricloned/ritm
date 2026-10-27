package com.ritm.app.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru-RU")

fun String.cap(): String = replaceFirstChar { it.titlecase(RU) }

fun DayOfWeek.shortRu(): String = getDisplayName(TextStyle.SHORT, RU).cap()
fun DayOfWeek.fullRu(): String = getDisplayName(TextStyle.FULL, RU).cap()

fun LocalDate.dayTitle(): String {
    val today = LocalDate.now()
    val base = format(DateTimeFormatter.ofPattern("d MMMM", RU))
    return when (this) {
        today -> "Сегодня, $base"
        today.plusDays(1) -> "Завтра, $base"
        today.minusDays(1) -> "Вчера, $base"
        else -> "${dayOfWeek.fullRu()}, $base"
    }
}

fun LocalDate.shortText(): String = format(DateTimeFormatter.ofPattern("d MMM, EE", RU))

fun LocalTime.hm(): String = format(DateTimeFormatter.ofPattern("HH:mm"))

fun Long.dateText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", RU))

fun Long.timeText(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

fun fmtNum(d: Double): String = try {
    BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
} catch (_: Exception) {
    d.toString()
}

fun String.cleanMd(): String = replace(Regex("(?m)^#{1,6}\\s*"), "").replace("**", "").replace("__", "")

fun pickDate(ctx: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        .apply { datePicker.firstDayOfWeek = Calendar.MONDAY }
        .show()
}

fun pickTime(ctx: Context, initial: LocalTime?, onPick: (LocalTime) -> Unit) {
    val t = initial ?: LocalTime.now().plusHours(1).withMinute(0)
    TimePickerDialog(ctx, { _, h, m -> onPick(LocalTime.of(h, m)) }, t.hour, t.minute, true).show()
}

/** Мягкий переключатель подвкладок в виде «таблетки». */
@Composable
fun SubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceVariant)
            .padding(4.dp)
    ) {
        titles.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) cs.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t,
                    color = if (sel) cs.onPrimary else cs.onSurfaceVariant,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = if (titles.size > 3) 12.sp else 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(color)
    if (onClick != null) m = m.clickable { onClick() }
    Column(m.padding(16.dp), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Свайп влево/вправо по области: вправо — назад, влево — вперёд. */
fun Modifier.swipeHorizontal(onPrev: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 70.dp.toPx() }
    val prev = rememberUpdatedState(onPrev)
    val next = rememberUpdatedState(onNext)
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (total > threshold) prev.value() else if (total < -threshold) next.value()
                total = 0f
            },
            onDragCancel = { total = 0f },
            onHorizontalDrag = { change, dx ->
                total += dx
                change.consume()
            },
        )
    }
}

/** Голосовой ввод через системное распознавание речи. Возвращает функцию запуска. */
@Composable
fun rememberDictation(prompt: String, onText: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val callback = rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val r = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!r.isNullOrBlank()) callback.value(r)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Распознавание речи недоступно — нажми микрофон на клавиатуре", Toast.LENGTH_LONG).show()
        }
    }
}

/** Добавляет надиктованный текст к уже написанному. */
fun appendDictated(old: String, add: String): String = if (old.isBlank()) add else old.trimEnd() + ". " + add

/** Строка «Идёт уже 1:23 · Отменить» под кнопкой ИИ. */
@Composable
fun AiProgressLine(key: String) {
    val started = UiState.aiStartedAt[key] ?: return
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(started) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val sec = ((now - started) / 1000).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Идёт ${sec / 60}:${(sec % 60).toString().padStart(2, '0')} · можно свернуть приложение",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.TextButton(onClick = { com.ritm.app.ai.AiJobs.cancel(key) }) {
            Text("Отменить", color = MaterialTheme.colorScheme.error)
        }
    }
}
