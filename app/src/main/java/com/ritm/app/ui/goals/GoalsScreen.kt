package com.ritm.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritm.app.data.Goal
import com.ritm.app.data.Store
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.Pill
import com.ritm.app.ui.SectionTitle
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.dateText

val GOAL_CATEGORIES = listOf("Спорт", "Упражнения", "Финансы", "Другое")

fun categoryEmoji(c: String): String = when (c) {
    "Спорт" -> "🏃"
    "Упражнения" -> "💪"
    "Финансы" -> "💰"
    else -> "✨"
}

@Composable
fun GoalsScreen() {
    val cs = MaterialTheme.colorScheme
    var filter by rememberSaveable { mutableStateOf("Все") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var toDelete by remember { mutableStateOf<Goal?>(null) }

    val list = Store.goals.filter { filter == "Все" || it.category == filter }
    val active = list.filter { it.doneAt == null }.sortedByDescending { it.createdAt }
    val done = list.filter { it.doneAt != null }.sortedByDescending { it.doneAt }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SoftCard(color = cs.primaryContainer) {
                    Text(
                        if (list.isEmpty()) "Поставь первую цель" else "Выполнено ${done.size} из ${list.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (list.isEmpty()) 0f else done.size.toFloat() / list.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = cs.primary,
                        trackColor = cs.surface,
                    )
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (listOf("Все") + GOAL_CATEGORIES).forEach { c ->
                        Pill(if (c == "Все") c else "${categoryEmoji(c)} $c", filter == c) { filter = c }
                    }
                }
            }
            item { SectionTitle("В процессе · ${active.size}") }
            if (active.isEmpty()) {
                item {
                    EmptyState("🎯", "Активных целей нет", "Нажми +, чтобы поставить цель: спорт, упражнение, деньги — что угодно")
                }
            }
            items(active, key = { it.id }) { g ->
                GoalRow(g, onToggle = { Store.toggleGoal(g.id) }, onClick = { editing = g }, onDelete = { toDelete = g })
            }
            if (done.isNotEmpty()) {
                item { SectionTitle("История · ${done.size}", Modifier.padding(top = 8.dp)) }
                items(done, key = { it.id }) { g ->
                    GoalRow(g, onToggle = { Store.toggleGoal(g.id) }, onClick = { editing = g }, onDelete = { toDelete = g })
                }
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
        ) { Icon(Icons.Rounded.Add, "Новая цель") }
    }

    if (adding) {
        GoalDialog(
            initial = null,
            defaultCategory = if (filter in GOAL_CATEGORIES) filter else GOAL_CATEGORIES.first(),
            onDismiss = { adding = false },
        ) { title, cat ->
            Store.addGoal(title, cat)
            adding = false
        }
    }
    editing?.let { g ->
        GoalDialog(initial = g, defaultCategory = g.category, onDismiss = { editing = null }) { title, cat ->
            Store.updateGoal(g.copy(title = title.trim(), category = cat))
            editing = null
        }
    }
    toDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить цель?") },
            text = { Text("«${g.title}» будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { Store.deleteGoal(g.id); toDelete = null }) {
                    Text("Удалить", color = cs.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun GoalRow(g: Goal, onToggle: () -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isDone = g.doneAt != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDone) cs.surfaceContainer else cs.surface)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isDone,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = cs.primary, uncheckedColor = cs.outline)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(categoryEmoji(g.category), fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    g.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    color = if (isDone) cs.onSurfaceVariant else cs.onSurface,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "Добавлена ${g.createdAt.dateText()}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.75f)
            )
            g.doneAt?.let {
                Text(
                    "Выполнена ${it.dateText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = cs.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    initial: Goal?,
    defaultCategory: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var cat by remember { mutableStateOf(defaultCategory) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая цель" else "Цель") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Например: подтянуться 15 раз") },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GOAL_CATEGORIES.forEach { c ->
                        Pill("${categoryEmoji(c)} $c", cat == c) { cat = c }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, cat) }, enabled = title.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
