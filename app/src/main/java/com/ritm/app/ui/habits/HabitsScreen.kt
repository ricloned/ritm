package com.ritm.app.ui.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritm.app.data.Attempt
import com.ritm.app.data.Habit
import com.ritm.app.data.Store
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.RU
import com.ritm.app.ui.SubTabs
import com.ritm.app.ui.UiState
import com.ritm.app.ui.cap
import com.ritm.app.ui.hm
import com.ritm.app.ui.pickTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

private val GOOD = Color(0xFF4CB782)
private val BAD = Color(0xFFE5637A)
private val FLAME = Color(0xFFFF8A3D)

private val HABIT_EMOJIS = listOf(
    "🔥", "🍩", "🚫", "💪", "🏃", "🧘", "💧", "📚", "😴", "🚭", "🍺", "📵",
    "🥗", "🧠", "💰", "🌅", "🧊", "✍️", "🎯", "⚡",
)

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", RU)
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE", RU)

private fun daysWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "день"
        m10 in 2..4 && m100 !in 12..14 -> "дня"
        else -> "дней"
    }
}

@Composable
fun HabitsScreen() {
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Сегодня", "Прогресс"), UiState.habitsSub) { UiState.habitsSub = it }
        when (UiState.habitsSub) {
            0 -> TodayHabits()
            else -> HabitsProgress()
        }
    }
}

// ======================= Сегодня =======================

@Composable
private fun TodayHabits() {
    val cs = MaterialTheme.colorScheme
    val today = UiState.today
    val habits = Store.habits
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var confirmFail by remember { mutableStateOf<Pair<Habit, LocalDate>?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero") { HeroCard(habits, today) }
            if (habits.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "🔥",
                        "Нет челленджей",
                        "Нажми +, задай цель вроде «не есть сладкое» или «тренироваться каждый день» и отмечай каждый день. Провал сжигает серию — начинается новая попытка."
                    )
                }
            }
            items(habits, key = { it.id }) { h ->
                HabitCard(
                    h, today,
                    onEdit = { editing = h },
                    onMark = { date, value ->
                        if (value == false) confirmFail = h to date else Store.markHabit(h.id, date, value)
                    },
                )
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = cs.primary,
            contentColor = cs.onPrimary,
            shape = RoundedCornerShape(18.dp),
        ) { Icon(Icons.Rounded.Add, "Новый челлендж") }
    }

    if (adding) HabitDialog(null, onDismiss = { adding = false })
    editing?.let { h -> HabitDialog(h, onDismiss = { editing = null }) }
    confirmFail?.let { (h, date) ->
        val streak = h.streak
        val attemptNo = h.attempts.size
        AlertDialog(
            onDismissRequest = { confirmFail = null },
            title = { Text("Провал? 💥") },
            text = {
                Text(
                    if (streak > 0) "Серия «${h.title}» — $streak ${daysWord(streak)} — сгорит. Начнётся попытка ${attemptNo + 1}."
                    else "Отметить день как проваленный? Начнётся попытка ${attemptNo + 1}."
                )
            },
            confirmButton = {
                Button(
                    onClick = { Store.markHabit(h.id, date, false); confirmFail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BAD)
                ) { Text("Да, провалил") }
            },
            dismissButton = { TextButton(onClick = { confirmFail = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun HeroCard(habits: List<Habit>, today: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val marked = habits.count { it.marks.containsKey(today) }
    val done = habits.count { it.marks[today] == true }
    val bestNow = habits.maxOfOrNull { it.streak } ?: 0
    val frac by animateFloatAsState(if (habits.isEmpty()) 0f else marked.toFloat() / habits.size, tween(600), label = "hero")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .size(130.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        )
        Column(Modifier.padding(18.dp)) {
            Text(
                today.format(FULL_DATE).cap(),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onPrimary.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (habits.isEmpty()) "Начни челлендж" else "$marked из ${habits.size}",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = cs.onPrimary
                )
                if (habits.isNotEmpty()) {
                    Text(
                        "  отмечено",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            if (habits.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        marked == habits.size && done == habits.size -> "Все цели на сегодня закрыты 💪"
                        bestNow > 0 -> "Лучшая текущая серия: $bestNow ${daysWord(bestNow)} 🔥"
                        else -> "Отметь сегодняшний день в каждом челлендже"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimary
                )
            }
        }
    }
}

@Composable
private fun HabitCard(h: Habit, today: LocalDate, onEdit: () -> Unit, onMark: (LocalDate, Boolean?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mark = h.marks[today]
    val cur = h.current
    var dayMenu by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(cs.primaryContainer)
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center
            ) { Text(h.emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onEdit() }
            ) {
                Text(h.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Попытка ${h.attempts.size}" + (cur?.let { " · с ${it.start.format(SHORT_DATE)}" } ?: "") +
                        if (h.remind) " · 🔔 ${LocalTime.of(h.remindMin / 60, h.remindMin % 60).hm()}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥 ${h.streak}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = FLAME)
                Text(daysWord(h.streak), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        // последние 7 дней
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 6 downTo 0) {
                val d = today.minusDays(i.toLong())
                val m = h.marks[d]
                val isToday = d == today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, RU).cap(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) cs.primary else cs.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when (m) {
                                    true -> GOOD
                                    false -> BAD
                                    null -> cs.surfaceVariant
                                }
                            )
                            .then(if (isToday && m == null) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier)
                            .clickable { dayMenu = d },
                        contentAlignment = Alignment.Center
                    ) {
                        when (m) {
                            true -> Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White)
                            false -> Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = Color.White)
                            null -> Text(
                                d.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when (mark) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onMark(today, true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GOOD, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Сделал", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { onMark(today, false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, BAD.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Rounded.Close, null, tint = BAD)
                    Spacer(Modifier.width(6.dp))
                    Text("Провалил", color = BAD, fontWeight = FontWeight.SemiBold)
                }
            }
            true -> StatusBar("✅ Сегодня сделано — так держать!", GOOD) { onMark(today, null) }
            false -> StatusBar("💥 Серия сгорела. Новая попытка — с завтрашнего дня", BAD) { onMark(today, null) }
        }
    }

    dayMenu?.let { d ->
        AlertDialog(
            onDismissRequest = { dayMenu = null },
            title = { Text(d.format(FULL_DATE).cap()) },
            text = { Text("Как прошёл этот день в «${h.title}»?") },
            confirmButton = {
                Button(
                    onClick = { onMark(d, true); dayMenu = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GOOD)
                ) { Text("Сделал") }
            },
            dismissButton = {
                Row {
                    if (h.marks.containsKey(d)) TextButton(onClick = { onMark(d, null); dayMenu = null }) { Text("Снять") }
                    TextButton(onClick = { onMark(d, false); dayMenu = null }) { Text("Провалил", color = BAD) }
                }
            },
        )
    }
}

@Composable
private fun StatusBar(text: String, color: Color, onUndo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo) { Text("Отменить", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ======================= Прогресс =======================

@Composable
private fun HabitsProgress() {
    val habits = Store.habits
    if (habits.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("📈", "Пока пусто", "Создай челлендж во вкладке «Сегодня» — здесь появятся серии, рекорды и история попыток.")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(habits, key = { it.id }) { h -> ProgressCard(h) }
    }
}

@Composable
private fun ProgressCard(h: Habit) {
    val cs = MaterialTheme.colorScheme
    val today = UiState.today
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Text(h.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("🔥", "${h.streak}", "серия", FLAME, Modifier.weight(1f))
            StatTile("🏆", "${h.best}", "рекорд", Color(0xFFE0A93B), Modifier.weight(1f))
            StatTile("🎯", "${h.attempts.size}", "попыток", cs.primary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text("Последние 5 недель", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Heatmap(h, today)
        Spacer(Modifier.height(16.dp))
        Text("Попытки", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val best = h.best.coerceAtLeast(1)
        h.attempts.withIndex().reversed().forEach { (i, a) -> AttemptRow(i + 1, a, h.daysIn(a, today), best, today) }
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Heatmap(h: Habit, today: LocalDate) {
    val cs = MaterialTheme.colorScheme
    // 5 недель, начиная с понедельника
    val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(4)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val m = h.marks[date]
                    val future = date.isAfter(today)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    future -> Color.Transparent
                                    m == true -> GOOD
                                    m == false -> BAD
                                    else -> cs.surfaceVariant
                                }
                            )
                            .then(if (date == today) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(6.dp)) else Modifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttemptRow(n: Int, a: Attempt, days: Int, best: Int, today: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val active = a.end == null
    val color = if (active) GOOD else BAD
    val anim by animateFloatAsState((days.toFloat() / best).coerceIn(0.04f, 1f), tween(700), label = "attempt")
    val span = ChronoUnit.DAYS.between(a.start, a.end ?: today) + 1
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("Попытка $n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (active) "с ${a.start.format(SHORT_DATE)} · идёт"
                else "${a.start.format(SHORT_DATE)} → ${a.end?.format(SHORT_DATE)} · сгорела 💥",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$days ${daysWord(days)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(cs.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(anim)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Brush.horizontalGradient(listOf(GOOD, FLAME)) else Brush.horizontalGradient(listOf(BAD, BAD)))
            )
        }
        if (span > days && days > 0) {
            Text(
                "отмечено $days из $span дн.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ======================= Создание / редактирование =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitDialog(initial: Habit?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: HABIT_EMOJIS.first()) }
    var remind by remember { mutableStateOf(initial?.remind ?: true) }
    var remindMin by remember { mutableStateOf(initial?.remindMin ?: (21 * 60)) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый челлендж" else "Челлендж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Например: не есть сладкое") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HABIT_EMOJIS.forEach { e ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (e == emoji) cs.primaryContainer else cs.surfaceVariant)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Напоминание каждый день", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (remind) {
                            Text(
                                "в ${LocalTime.of(remindMin / 60, remindMin % 60).hm()} · нажми, чтобы изменить",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary,
                                modifier = Modifier.clickable {
                                    pickTime(ctx, LocalTime.of(remindMin / 60, remindMin % 60)) { t -> remindMin = t.hour * 60 + t.minute }
                                }
                            )
                        }
                    }
                    Switch(checked = remind, onCheckedChange = { remind = it })
                }
                Text(
                    "Не придёт, если день уже отмечен.",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (initial == null) Store.addHabit(title, emoji, remind, remindMin)
                else Store.updateHabit(initial.copy(title = title.trim(), emoji = emoji, remind = remind, remindMin = remindMin))
                onDismiss()
            }, enabled = title.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = cs.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить челлендж?") },
            text = { Text("«${initial.title}» и вся история попыток будут удалены.") },
            confirmButton = {
                TextButton(onClick = { Store.deleteHabit(initial.id); confirmDelete = false; onDismiss() }) {
                    Text("Удалить", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
