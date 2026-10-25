package com.ritm.app.ui.workout

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ritm.app.data.Exercise
import com.ritm.app.data.SetEntry
import com.ritm.app.data.Store
import com.ritm.app.data.WorkoutLog
import com.ritm.app.data.lineText
import com.ritm.app.data.toText
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.UiState
import com.ritm.app.ui.cleanMd
import com.ritm.app.ui.dayTitle
import com.ritm.app.ui.fullRu
import com.ritm.app.ui.pickDate
import com.ritm.app.ui.shortRu
import com.ritm.app.ui.shortText
import java.time.DayOfWeek

// ======================= Сегодня (круговая тренировка) =======================

@Composable
fun TodayTab(onOpenProgram: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val today = UiState.today
    val programDay = UiState.todayProgramDay
    val exercises = Store.program[programDay].orEmpty()
    val todayLog = Store.logs.filter { it.date == today }.maxByOrNull { it.finishedAt }

    if (todayLog != null && !UiState.workoutAgain) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SoftCard {
                    Text(
                        "День ${todayLog.dayNumber} завершён 🎉",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LogContent(todayLog)
                }
            }
            item {
                OutlinedButton(
                    onClick = { UiState.workoutAgain = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Записать ещё одну тренировку") }
            }
        }
        return
    }

    val allSets = exercises.map { Store.setsFor(today, it) }
    val rounds = allSets.maxOfOrNull { it.size } ?: 0
    val doneSets = allSets.sumOf { l -> l.count { it.done } }
    val totalSets = allSets.sumOf { it.size }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Column {
                Text(
                    "День ${Store.nextDayNumber()} · ${DayOfWeek.of(programDay).fullRu()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (exercises.isEmpty()) today.dayTitle()
                    else "${today.dayTitle()} · ${exercises.size} упр. · кругов $rounds · готово $doneSets из $totalSets",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                DayChips(selected = programDay, todayDay = today.dayOfWeek.value) { UiState.todayProgramDay = it }
            }
        }

        if (exercises.isEmpty()) {
            item(key = "rest") {
                EmptyState(
                    "😌",
                    "В этот день отдых",
                    "Выбери другой день программы выше или настрой программу.",
                ) {
                    Button(onClick = onOpenProgram) { Text("Открыть программу") }
                }
            }
            return@LazyColumn
        }

        for (r in 0 until rounds) {
            item(key = "round$r") {
                RoundCard(r, exercises.filter { Store.setsFor(today, it).size > r })
            }
        }

        item(key = "roundButtons") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { Store.addRound(today, exercises) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Круг")
                }
                OutlinedButton(
                    onClick = { Store.removeRound(today, exercises) },
                    enabled = rounds > 1,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Remove, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Убрать круг")
                }
            }
        }

        item(key = "finish") {
            Button(
                onClick = {
                    val log = Store.finishWorkout(today, exercises)
                    if (log == null) {
                        Toast.makeText(ctx, "Заполни хотя бы одно упражнение", Toast.LENGTH_SHORT).show()
                    } else {
                        UiState.workoutAgain = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("Завершить и получить отчёт")
            }
        }
    }
}

@Composable
private fun DayChips(selected: Int, todayDay: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in 1..7) {
            val sel = d == selected
            val count = Store.program[d].orEmpty().size
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            sel -> cs.primary
                            count > 0 -> cs.primaryContainer
                            else -> cs.surfaceVariant
                        }
                    )
                    .clickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    DayOfWeek.of(d).shortRu(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (d == todayDay) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) cs.onPrimary else cs.onSurface
                )
                Text(
                    if (count > 0) "$count" else "–",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sel) cs.onPrimary else cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RoundCard(round: Int, exercises: List<Exercise>) {
    val cs = MaterialTheme.colorScheme
    val today = UiState.today
    val done = exercises.count { Store.setsFor(today, it).getOrNull(round)?.done == true }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Круг ${round + 1}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (done == exercises.size) cs.primary else cs.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "$done/${exercises.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done == exercises.size) cs.onPrimary else cs.onPrimaryContainer
                )
            }
        }
        exercises.forEachIndexed { i, ex ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = cs.outlineVariant)
            else Spacer(Modifier.height(10.dp))
            ExerciseInRound(ex, round)
        }
    }
}

@Composable
private fun ExerciseInRound(ex: Exercise, round: Int) {
    val cs = MaterialTheme.colorScheme
    val today = UiState.today
    val set = Store.setsFor(today, ex).getOrNull(round) ?: SetEntry()
    val (hintW, hintR) = Store.hint(ex, round)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ex.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val next = if (!set.done) {
                        set.copy(
                            weight = set.weight.ifBlank { hintW },
                            reps = set.reps.ifBlank { hintR },
                            done = true,
                        )
                    } else set.copy(done = false)
                    if (next.weight != set.weight) Store.setWeight(today, ex, round, next.weight)
                    Store.updateSet(today, ex, round, next)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (set.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "Готово",
                    tint = if (set.done) cs.primary else cs.outline,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactField(
                value = set.weight,
                placeholder = hintW.ifBlank { "без веса" },
                suffix = "кг",
                keyboard = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            ) { v ->
                Store.setWeight(today, ex, round, v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(6))
            }
            CompactField(
                value = set.reps,
                placeholder = hintR,
                suffix = "раз",
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            ) { v ->
                Store.updateSet(today, ex, round, set.copy(reps = v.filter { it.isDigit() }.take(4)))
            }
        }
        Spacer(Modifier.height(6.dp))
        CompactField(
            value = set.comment,
            placeholder = "комментарий",
            suffix = "",
            keyboard = KeyboardType.Text,
            modifier = Modifier.fillMaxWidth(),
            small = true,
        ) { v ->
            Store.updateSet(today, ex, round, set.copy(comment = v.take(120)))
        }
    }
}

@Composable
private fun CompactField(
    value: String,
    placeholder: String,
    suffix: String,
    keyboard: KeyboardType,
    modifier: Modifier,
    small: Boolean = false,
    onChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val style = (if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium)
        .copy(color = cs.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(cs.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = if (small) 8.dp else 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = style.copy(color = cs.onSurfaceVariant.copy(alpha = 0.5f)))
                    }
                    inner()
                }
                if (suffix.isNotEmpty()) {
                    Text(suffix, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        },
    )
}

// ======================= Запись тренировки и отчёт =======================

@Composable
fun LogContent(log: WorkoutLog) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val rounds = log.exercises.maxOfOrNull { it.sets.size } ?: 0
    Column {
        for (r in 0 until rounds) {
            Text(
                "Круг ${r + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                modifier = Modifier.padding(top = if (r == 0) 0.dp else 10.dp, bottom = 2.dp)
            )
            log.exercises.forEach { e ->
                val s = e.sets.getOrNull(r) ?: return@forEach
                Text(
                    buildAnnotatedString {
                        append(e.name + " ")
                        val w = s.weight.toDoubleOrNull()
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(
                                if (w != null && w > 0) "${com.ritm.app.ui.fmtNum(w)}кг на ${s.reps}" else "на ${s.reps}"
                            )
                        }
                        if (s.comment.isNotBlank()) {
                            withStyle(SpanStyle(color = cs.onSurfaceVariant, fontStyle = FontStyle.Italic)) {
                                append(" (${s.comment.trim()})")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
        val skipped = log.exercises.filter { it.sets.isEmpty() }
        if (skipped.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Пропущено: " + skipped.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = cs.error
            )
        }
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(log.toText()))
                Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
            },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Скопировать текстом", style = MaterialTheme.typography.labelMedium)
        }
        ReportBlock(log)
    }
}

@Composable
fun ReportBlock(log: WorkoutLog) {
    val cs = MaterialTheme.colorScheme
    val loading = log.id in Store.reportLoading
    val err = Store.reportErrors[log.id]
    val report = log.report
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.primaryContainer.copy(alpha = 0.7f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = cs.primary)
            Spacer(Modifier.width(6.dp))
            Text("Отчёт ИИ", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.weight(1f))
            if (report != null && !loading) {
                IconButton(onClick = { Store.generateReport(log.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Refresh, "Обновить отчёт", Modifier.size(18.dp), tint = cs.primary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text("Пишу отчёт…", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
            }
            report != null -> Text(report.cleanMd(), style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
            else -> {
                Text(
                    err ?: "Отчёта пока нет",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (err != null) cs.error else cs.onPrimaryContainer
                )
                TextButton(onClick = { Store.generateReport(log.id) }) { Text("Сгенерировать отчёт") }
            }
        }
    }
}

// ======================= История =======================

@Composable
fun HistoryTab() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var toDelete by remember { mutableStateOf<WorkoutLog?>(null) }
    var importing by remember { mutableStateOf(false) }
    val logs = Store.logs.sortedWith(
        compareByDescending<WorkoutLog> { it.dayNumber }.thenByDescending { it.finishedAt }
    )

    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                "📈",
                "Пока нет тренировок",
                "Заверши первую тренировку или загрузи прошлые из текста в формате «День 8 / Круг 1 / Ноги тренажер 75кг на 15»."
            ) {
                Button(onClick = { importing = true }) { Text("Загрузить прошлые тренировки") }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "import") {
                TextButton(onClick = { importing = true }) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Загрузить прошлые тренировки из текста")
                }
            }
            items(logs, key = { it.id }) { log ->
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (log.dayNumber > 0) "День ${log.dayNumber}" else "Тренировка",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { pickDate(ctx, log.date) { Store.updateLogDate(log.id, it) } }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    log.date.dayTitle(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { toDelete = log }) {
                            Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = cs.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = cs.outlineVariant)
                    LogContent(log)
                }
            }
        }
    }

    toDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить тренировку?") },
            text = { Text("«День ${log.dayNumber}» будет удалён.") },
            confirmButton = {
                TextButton(onClick = { Store.deleteLog(log.id); toDelete = null }) {
                    Text("Удалить", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }

    if (importing) {
        ImportDialog(onDismiss = { importing = false })
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var lastDate by remember { mutableStateOf(UiState.today.minusDays(1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Загрузить тренировки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Вставь записи в формате:\nДень 8\nКруг 1\nНоги тренажер 75кг на 15 (коммент)\nТрицепс брусья на 12",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text("Вставь текст сюда") },
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedButton(onClick = { pickDate(ctx, lastDate) { lastDate = it } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Последний день был: ${lastDate.shortText()}")
                }
                Text(
                    "Даты остальных дней проставятся примерно (через день), их можно поправить нажатием на дату.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = Store.importLogs(text, lastDate)
                if (n == 0) {
                    Toast.makeText(ctx, "Не нашёл тренировок в тексте", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, "Загружено дней: $n", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }, enabled = text.isNotBlank()) { Text("Загрузить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Suppress("unused")
private fun previewLine(s: SetEntry): String = s.lineText()
