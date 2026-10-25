package com.ritm.app.ui.workout

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.heightIn
import com.ritm.app.ui.UiState
import com.ritm.app.ui.appendDictated
import com.ritm.app.ui.rememberDictation
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ritm.app.ai.AiClient
import com.ritm.app.data.Exercise
import com.ritm.app.data.SetEntry
import com.ritm.app.data.Store
import com.ritm.app.data.WorkoutLog
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.SubTabs
import com.ritm.app.ui.cleanMd
import com.ritm.app.ui.dayTitle
import com.ritm.app.ui.fmtNum
import com.ritm.app.ui.fullRu
import com.ritm.app.ui.shortRu
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun WorkoutScreen() {
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Сегодня", "Питание", "Программа", "История"), UiState.workoutSub) { UiState.workoutSub = it }
        when (UiState.workoutSub) {
            0 -> TodayTab(onOpenProgram = { UiState.workoutSub = 2 })
            1 -> FoodTab()
            2 -> ProgramTab()
            else -> HistoryTab()
        }
    }
}

fun targetText(ex: Exercise): String =
    "${ex.sets}×${ex.reps}" + if (ex.weight > 0) " · ${fmtNum(ex.weight)} кг" else ""

// ======================= Сегодня =======================

@Composable
private fun TodayTab(onOpenProgram: () -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val exercises = Store.program[today.dayOfWeek.value].orEmpty()
    val todayLog = Store.logs.firstOrNull { it.date == today }
    var again by rememberSaveable { mutableStateOf(false) }

    if (exercises.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                "😌",
                "Сегодня день отдыха",
                "На ${today.dayOfWeek.fullRu().lowercase()} упражнения не запланированы. Настрой программу тренировок по дням.",
            ) {
                Button(onClick = onOpenProgram) { Text("Открыть программу") }
            }
        }
        return
    }

    if (todayLog != null && !again) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SoftCard {
                    Text("Тренировка завершена 🎉", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    LogContent(todayLog)
                }
            }
            item {
                OutlinedButton(
                    onClick = { again = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Записать ещё одну тренировку") }
            }
        }
        return
    }

    val allSets = exercises.map { Store.setsFor(today, it) }
    val doneSets = allSets.sumOf { l -> l.count { it.done } }
    val totalSets = allSets.sumOf { it.size }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(today.dayTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${exercises.size} упражнений · выполнено подходов $doneSets из $totalSets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(exercises, key = { it.id }) { ex -> ExerciseCard(today, ex) }
        item {
            Button(
                onClick = {
                    val log = Store.finishWorkout(today, exercises)
                    if (log == null) {
                        Toast.makeText(ctx, "Заполни хотя бы один подход", Toast.LENGTH_SHORT).show()
                    } else {
                        again = false
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
private fun ExerciseCard(date: LocalDate, ex: Exercise) {
    val cs = MaterialTheme.colorScheme
    val sets = Store.setsFor(date, ex)
    val doneCount = sets.count { it.done }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Цель: ${targetText(ex)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (doneCount == sets.size) cs.primary else cs.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "$doneCount/${sets.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (doneCount == sets.size) cs.onPrimary else cs.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        sets.forEachIndexed { i, s ->
            SetRow(i, s, ex) { Store.updateSet(date, ex, i, it) }
        }
        Row {
            TextButton(onClick = { Store.updateSet(date, ex, sets.size, SetEntry()) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Подход")
            }
            if (sets.size > 1) {
                TextButton(onClick = { Store.removeLastSet(date, ex) }) {
                    Text("Убрать подход", color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SetRow(index: Int, s: SetEntry, ex: Exercise, onChange: (SetEntry) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (s.done) cs.primary else cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = if (s.done) cs.onPrimary else cs.onPrimaryContainer
            )
        }
        OutlinedTextField(
            value = s.reps,
            onValueChange = { v -> onChange(s.copy(reps = v.filter { it.isDigit() }.take(4))) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(ex.reps.toString(), color = cs.onSurfaceVariant.copy(alpha = 0.5f)) },
            suffix = { Text("повт", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = s.weight,
            onValueChange = { v ->
                onChange(s.copy(weight = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(6)))
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text(if (ex.weight > 0) fmtNum(ex.weight) else "0", color = cs.onSurfaceVariant.copy(alpha = 0.5f))
            },
            suffix = { Text("кг", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
        )
        IconButton(onClick = {
            val next = if (!s.done) {
                s.copy(
                    reps = s.reps.ifBlank { ex.reps.toString() },
                    weight = if (s.weight.isBlank() && ex.weight > 0) fmtNum(ex.weight) else s.weight,
                    done = true,
                )
            } else s.copy(done = false)
            onChange(next)
        }) {
            Icon(
                if (s.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = "Подход выполнен",
                tint = if (s.done) cs.primary else cs.outline,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ======================= Отчёт / лог =======================

@Composable
fun LogContent(log: WorkoutLog) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        log.exercises.forEach { e ->
            Column {
                Text(e.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val details = if (e.sets.isEmpty()) "пропущено" else e.sets.joinToString("  ·  ") { s ->
                    val w = s.weight.toDoubleOrNull()
                    if (w != null && w > 0) "${s.reps}×${fmtNum(w)}" else "${s.reps} повт"
                }
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (e.sets.isEmpty()) cs.error else cs.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
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
private fun HistoryTab() {
    var toDelete by remember { mutableStateOf<WorkoutLog?>(null) }
    val logs = Store.logs
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("📈", "Пока нет тренировок", "Заверши первую тренировку — здесь появится история и отчёты ИИ")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(logs, key = { it.id }) { log ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(log.date.dayTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val total = log.exercises.sumOf { it.sets.size }
                        Text(
                            "${log.exercises.count { it.sets.isNotEmpty() }} упр. · $total подходов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { toDelete = log }) {
                        Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                LogContent(log)
            }
        }
    }
    toDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить тренировку?") },
            text = { Text("Запись за ${log.date.dayTitle().lowercase()} будет удалена.") },
            confirmButton = {
                TextButton(onClick = { Store.deleteLog(log.id); toDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

// ======================= Программа =======================

@Composable
private fun ProgramTab() {
    val cs = MaterialTheme.colorScheme
    if (UiState.programAiOpen) {
        BackHandler { UiState.programAiOpen = false }
        AiProgramPanel(onClose = { UiState.programAiOpen = false })
        return
    }
    val day = UiState.programDay
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    val list = Store.program[day].orEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 1..7) {
                        val sel = d == day
                        val count = Store.program[d].orEmpty().size
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        sel -> cs.primary
                                        count > 0 -> cs.primaryContainer
                                        else -> cs.surfaceVariant
                                    }
                                )
                                .clickable { UiState.programDay = d }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                DayOfWeek.of(d).shortRu(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
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
            item {
                Text(
                    DayOfWeek.of(day).fullRu() + if (list.isEmpty()) " · отдых" else " · ${list.size} упр.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (list.isEmpty()) {
                item {
                    EmptyState("🗓️", "Нет упражнений", "Нажми +, чтобы добавить упражнение вручную или составить всю программу с ИИ")
                }
            }
            itemsIndexed(list, key = { _, e -> e.id }) { i, ex ->
                SoftCard(onClick = { editing = ex }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(targetText(ex), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        IconButton(onClick = { Store.moveExercise(day, i, -1) }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Выше")
                        }
                        IconButton(onClick = { Store.moveExercise(day, i, 1) }, enabled = i < list.size - 1) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Ниже")
                        }
                        IconButton(onClick = { Store.deleteExercise(day, ex.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            FloatingActionButton(
                onClick = { menu = true },
                containerColor = cs.primary,
                contentColor = cs.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.Add, "Добавить")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Добавить упражнение") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = {
                        menu = false
                        adding = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Составить программу с ИИ") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = cs.primary) },
                    onClick = {
                        menu = false
                        UiState.programAiOpen = true
                    },
                )
            }
        }
    }

    if (adding) {
        ExerciseDialog(null, onDismiss = { adding = false }) { ex ->
            Store.saveExercise(day, ex)
            adding = false
        }
    }
    editing?.let { e ->
        ExerciseDialog(e, onDismiss = { editing = null }) { ex ->
            Store.saveExercise(day, ex)
            editing = null
        }
    }
}

@Composable
private fun ExerciseDialog(initial: Exercise?, onDismiss: () -> Unit, onSave: (Exercise) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var sets by remember { mutableStateOf(initial?.sets?.toString() ?: "3") }
    var reps by remember { mutableStateOf(initial?.reps?.toString() ?: "10") }
    var weight by remember { mutableStateOf(initial?.weight?.takeIf { it > 0 }?.let { fmtNum(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новое упражнение" else "Упражнение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Жим лёжа") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { v -> sets = v.filter { it.isDigit() }.take(2) },
                        label = { Text("Подходы") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { v -> reps = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Повт.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v -> weight = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(6) },
                        label = { Text("Кг") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                Text(
                    "Вес можно оставить пустым (например, для подтягиваний или планки).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        Exercise(
                            id = initial?.id ?: Store.newId(),
                            name = name.trim(),
                            sets = (sets.toIntOrNull() ?: 3).coerceIn(1, 20),
                            reps = (reps.toIntOrNull() ?: 10).coerceAtLeast(1),
                            weight = weight.toDoubleOrNull() ?: 0.0,
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ======================= ИИ-программа =======================

@Composable
private fun AiProgramPanel(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val text = Store.programDraft
    val result = UiState.programResult
    val loading = UiState.programLoading
    val error = UiState.programError
    var confirm by remember { mutableStateOf(false) }
    val dictate = rememberDictation("Расскажи, как ты занимаешься и чего хочешь…") { r ->
        Store.updateProgramDraft(appendDictated(Store.programDraft, r))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Text("Программа с ИИ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            SoftCard(color = cs.primaryContainer) {
                Text(
                    "Расскажи, как ты сейчас занимаешься: упражнения, веса, повторения, сколько раз в неделю. " +
                        "Добавь цели и пожелания (масса, сила, рельеф, больная спина, нет штанги, только 3 дня…). " +
                        "ИИ составит программу на неделю, ты посмотришь и применишь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer
                )
            }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { Store.updateProgramDraft(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                placeholder = {
                    Text("Например: жму 60 кг на 8, приседаю 70 на 10, подтягиваюсь 8 раз. Хожу пн/ср/пт по часу. Хочу набрать массу и подтянуться 15 раз, плечо иногда побаливает.")
                },
                shape = RoundedCornerShape(18.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = dictate,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Mic, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Надиктовать")
                }
                Button(
                    onClick = {
                        val request = text
                        val current = Store.program
                        Store.scope.launch {
                            UiState.programLoading = true
                            UiState.programError = null
                            try {
                                UiState.programResult = AiClient.buildProgram(request, current)
                            } catch (e: Exception) {
                                UiState.programError = e.message ?: "Ошибка"
                            } finally {
                                UiState.programLoading = false
                            }
                        }
                    },
                    enabled = text.isNotBlank() && !loading,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, null)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (loading) "Составляю…" else "Составить")
                }
            }
        }
        if (error != null) {
            item {
                SoftCard(color = cs.errorContainer) {
                    Text(error, color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (result != null) {
            if (result.comment.isNotBlank()) {
                item {
                    ReportLike(result.comment)
                }
            }
            items((1..7).toList(), key = { "d$it" }) { d ->
                val list = result.days[d].orEmpty()
                SoftCard {
                    Text(
                        DayOfWeek.of(d).fullRu() + if (list.isEmpty()) " · отдых" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (list.isEmpty()) cs.onSurfaceVariant else cs.primary
                    )
                    list.forEach { ex ->
                        Row(Modifier.padding(top = 6.dp)) {
                            Text(ex.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(targetText(ex), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { confirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Применить программу") }
            }
        }
    }

    if (confirm && result != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Заменить программу?") },
            text = { Text("Текущая программа на все дни недели будет заменена новой. История тренировок сохранится.") },
            confirmButton = {
                Button(onClick = {
                    Store.replaceProgram(result.days)
                    UiState.programResult = null
                    confirm = false
                    onClose()
                }) { Text("Заменить") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ReportLike(text: String) {
    val cs = MaterialTheme.colorScheme
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
            Text("Комментарий тренера", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(text.cleanMd(), style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
    }
}
