package com.ritm.app.ui.plans

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ritm.app.ai.AiClient
import com.ritm.app.data.Store
import com.ritm.app.data.Task
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.RU
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.SubTabs
import com.ritm.app.ui.cap
import com.ritm.app.ui.dayTitle
import com.ritm.app.ui.hm
import com.ritm.app.ui.pickDate
import com.ritm.app.ui.pickTime
import com.ritm.app.ui.shortRu
import com.ritm.app.ui.shortText
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun PlansScreen() {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Задачи", "ИИ-планер"), sub) { sub = it }
        when (sub) {
            0 -> TasksTab()
            else -> AiPlannerTab(onAdded = { sub = 0 })
        }
    }
}

// ======================= Задачи =======================

@Composable
private fun TasksTab() {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var selected by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var weekStart by rememberSaveable { mutableStateOf(LocalDate.now().with(DayOfWeek.MONDAY)) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    val dayTasks = Store.tasks.filter { it.date == selected }
        .sortedWith(compareBy<Task>({ it.done }, { it.time ?: LocalTime.MAX }, { it.createdAt }))
    val doneCount = dayTasks.count { it.done }

    fun select(d: LocalDate) {
        selected = d
        weekStart = d.with(DayOfWeek.MONDAY)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                WeekStrip(
                    weekStart = weekStart,
                    selected = selected,
                    today = today,
                    onWeek = { weekStart = it },
                    onSelect = { selected = it },
                    onPick = { pickDate(ctx, selected) { select(it) } },
                    onToday = { select(today) },
                )
            }
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(selected.dayTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (dayTasks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { doneCount.toFloat() / dayTasks.size },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "$doneCount из ${dayTasks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (dayTasks.isEmpty()) {
                item {
                    EmptyState(
                        "🌿",
                        "Задач нет",
                        "Нажми +, чтобы добавить задачу, или надиктуй планы во вкладке «ИИ-планер»"
                    )
                }
            }
            items(dayTasks, key = { it.id }) { t ->
                TaskRow(
                    t,
                    onToggle = { Store.toggleTask(t.id) },
                    onClick = { editing = t },
                    onDelete = { Store.deleteTask(t.id) },
                )
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Добавить задачу")
        }
    }

    if (adding) {
        TaskDialog(
            initial = null,
            defaultDate = selected,
            onDismiss = { adding = false },
            onSave = { title, date, time ->
                Store.addTask(title, date, time)
                adding = false
            },
            onDelete = null,
        )
    }
    editing?.let { t ->
        TaskDialog(
            initial = t,
            defaultDate = t.date,
            onDismiss = { editing = null },
            onSave = { title, date, time ->
                Store.updateTask(t.copy(title = title.trim(), date = date, time = time))
                editing = null
            },
            onDelete = {
                Store.deleteTask(t.id)
                editing = null
            },
        )
    }
}

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    onWeek: (LocalDate) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onPick: () -> Unit,
    onToday: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onWeek(weekStart.minusWeeks(1)) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Прошлая неделя")
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick() }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp), tint = cs.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    weekStart.plusDays(3).format(DateTimeFormatter.ofPattern("LLLL yyyy", RU)).cap(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (selected != today) {
                TextButton(onClick = onToday) { Text("Сегодня") }
            }
            IconButton(onClick = { onWeek(weekStart.plusWeeks(1)) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Следующая неделя")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val d = weekStart.plusDays(i.toLong())
                val sel = d == selected
                val isToday = d == today
                val hasOpen = Store.tasks.any { it.date == d && !it.done }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                sel -> cs.primary
                                isToday -> cs.primaryContainer
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onSelect(d) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        d.dayOfWeek.shortRu(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) cs.onPrimary else cs.onSurfaceVariant
                    )
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sel) cs.onPrimary else cs.onSurface
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !hasOpen -> Color.Transparent
                                    sel -> cs.onPrimary
                                    else -> cs.primary
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(t: Task, onToggle: () -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = t.done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = cs.primary, uncheckedColor = cs.outline)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            Text(
                t.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                color = if (t.done) cs.onSurfaceVariant else cs.onSurface,
            )
            if (t.time != null || t.carried > 0) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.time?.let { time ->
                        Chip(Icons.Rounded.Schedule, time.hm(), cs.primaryContainer, cs.onPrimaryContainer)
                    }
                    if (t.carried > 0) {
                        Chip(
                            null,
                            if (t.carried == 1) "перенесено" else "перенесено ×${t.carried}",
                            cs.tertiaryContainer,
                            cs.onTertiaryContainer
                        )
                    }
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить", tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, bg: Color, fg: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = fg)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun TaskDialog(
    initial: Task?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, LocalTime?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var time by remember { mutableStateOf(initial?.time) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая задача" else "Задача") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Что нужно сделать?") },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate(ctx, date) { date = it } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(date.shortText(), maxLines = 1)
                    }
                    OutlinedButton(onClick = { pickTime(ctx, time) { time = it } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(time?.hm() ?: "Время", maxLines = 1)
                    }
                    if (time != null) {
                        IconButton(onClick = { time = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Убрать время")
                        }
                    }
                }
                if (time != null) {
                    Text(
                        "🔔 Напомню в ${time?.hm()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, date, time) }, enabled = title.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

// ======================= ИИ-планер =======================

@Composable
private fun AiPlannerTab(onAdded: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<AiClient.PlanItem>>(emptyList()) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val r = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!r.isNullOrBlank()) text = if (text.isBlank()) r else text.trimEnd() + ". " + r
        }
    }

    fun startDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Рассказывай, что нужно сделать…")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
        try {
            speech.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Распознавание речи недоступно — нажми микрофон на клавиатуре", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoftCard(color = cs.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = cs.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Расскажи свои планы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onPrimaryContainer)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Надиктуй или напиши всё подряд — ИИ разобьёт на задачи и разложит по дням и времени. Можно надиктовывать несколько раз, текст дописывается.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer
                )
            }
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                placeholder = {
                    Text("Например: завтра в 10 созвон с Димой, в пятницу оплатить интернет, в выходные съездить к родителям, каждый день читать 20 минут…")
                },
                shape = RoundedCornerShape(18.dp),
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "Очистить") }
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { startDictation() },
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
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                val res = AiClient.parsePlan(text)
                                items = res
                                if (res.isEmpty()) error = "Не нашёл задач в тексте"
                            } catch (e: Exception) {
                                error = e.message ?: "Ошибка"
                            } finally {
                                loading = false
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
                    Text(if (loading) "Думаю…" else "Разобрать")
                }
            }
        }
        error?.let { msg ->
            item {
                SoftCard(color = cs.errorContainer) {
                    Text(msg, color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (items.isNotEmpty()) {
            item {
                Text(
                    "Нашёл задач: ${items.size}. Сними галочки с лишних:",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            itemsIndexed(items) { i, it ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surface)
                        .clickable {
                            items = items.toMutableList().also { l -> l[i] = it.copy(selected = !it.selected) }
                        }
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = it.selected,
                        onCheckedChange = { _ ->
                            items = items.toMutableList().also { l -> l[i] = it.copy(selected = !it.selected) }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = cs.primary, uncheckedColor = cs.outline)
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            it.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (it.selected) cs.onSurface else cs.onSurfaceVariant
                        )
                        Text(
                            it.date.dayTitle() + (it.time?.let { t -> " · " + t.hm() } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.primary
                        )
                    }
                }
            }
            item {
                val count = items.count { it.selected }
                Button(
                    onClick = {
                        Store.addTasks(items.filter { it.selected }.map { Triple(it.title, it.date, it.time) })
                        Toast.makeText(ctx, "Добавлено задач: $count", Toast.LENGTH_SHORT).show()
                        items = emptyList()
                        text = ""
                        onAdded()
                    },
                    enabled = count > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Добавить в план ($count)", textAlign = TextAlign.Center)
                }
            }
        }
    }
}
