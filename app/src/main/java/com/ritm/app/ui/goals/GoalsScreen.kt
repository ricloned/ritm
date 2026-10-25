package com.ritm.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin as PushPinOutlined
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ritm.app.data.Goal
import com.ritm.app.data.Store
import com.ritm.app.ui.EmptyState
import com.ritm.app.ui.SectionTitle
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.dateText

val GOAL_EMOJIS = listOf(
    "🎯", "💪", "🏃", "🏋️", "🧘", "🚴", "🏊", "⚽",
    "💰", "💳", "🏠", "🚗", "📚", "🧠", "💼", "✈️",
    "❤️", "🍎", "😴", "🏆", "⭐", "🚀", "🎨", "✨",
)

/** Перетаскивание целей за «ручку». */
private class ReorderState(val listState: LazyListState) {
    var draggingId by mutableStateOf<Long?>(null)
    var offset by mutableFloatStateOf(0f)

    fun onDrag(dy: Float, group: List<Long>, move: (Long, Long) -> Unit) {
        val id = draggingId ?: return
        offset += dy
        val visible = listState.layoutInfo.visibleItemsInfo
        val cur = visible.firstOrNull { it.key == id } ?: return
        val center = cur.offset + offset + cur.size / 2f
        val target = visible.firstOrNull { info ->
            val key = info.key as? Long
            key != null && key != id && key in group &&
                center > info.offset && center < info.offset + info.size
        } ?: return
        val targetKey = target.key as Long
        move(id, targetKey)
        offset -= (target.offset - cur.offset).toFloat()
    }

    fun stop() {
        draggingId = null
        offset = 0f
    }
}

@Composable
fun GoalsScreen() {
    val cs = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var toDelete by remember { mutableStateOf<Goal?>(null) }
    val listState = rememberLazyListState()
    val reorder = remember(listState) { ReorderState(listState) }

    val all = Store.goals
    val pinned = all.filter { it.doneAt == null && it.pinned }
    val active = all.filter { it.doneAt == null && !it.pinned }
    val done = all.filter { it.doneAt != null }.sortedByDescending { it.doneAt }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "progress") {
                SoftCard(color = cs.primaryContainer) {
                    Text(
                        if (all.isEmpty()) "Поставь первую цель" else "Выполнено ${done.size} из ${all.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (all.isEmpty()) 0f else done.size.toFloat() / all.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = cs.primary,
                        trackColor = cs.surface,
                    )
                    if (pinned.size + active.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Тяни за ⠿, чтобы поменять порядок. 📌 — закрепить самое важное сверху.",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            if (pinned.isNotEmpty()) {
                item(key = "h_pinned") { SectionTitle("📌 Закреплённые") }
                items(pinned, key = { it.id }) { g ->
                    ActiveGoalRow(g, pinned.map { it.id }, reorder, onEdit = { editing = g })
                }
            }
            item(key = "h_active") { SectionTitle(if (pinned.isEmpty()) "Цели · ${active.size}" else "Остальные · ${active.size}") }
            if (active.isEmpty() && pinned.isEmpty()) {
                item(key = "empty") {
                    EmptyState("🎯", "Активных целей нет", "Нажми +, чтобы поставить цель: спорт, упражнение, деньги — что угодно")
                }
            }
            items(active, key = { it.id }) { g ->
                ActiveGoalRow(g, active.map { it.id }, reorder, onEdit = { editing = g })
            }
            if (done.isNotEmpty()) {
                item(key = "h_done") { SectionTitle("История · ${done.size}", Modifier.padding(top = 8.dp)) }
                items(done, key = { it.id }) { g ->
                    DoneGoalRow(g, onDelete = { toDelete = g })
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
        GoalDialog(initial = null, onDismiss = { adding = false }, onDelete = null) { title, emoji ->
            Store.addGoal(title, emoji)
            adding = false
        }
    }
    editing?.let { g ->
        GoalDialog(
            initial = g,
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                toDelete = g
            },
        ) { title, emoji ->
            Store.updateGoal(g.copy(title = title.trim(), emoji = emoji))
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
private fun ActiveGoalRow(g: Goal, group: List<Long>, reorder: ReorderState, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dragging = reorder.draggingId == g.id
    val currentGroup = rememberUpdatedState(group)
    Row(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) reorder.offset else 0f }
            .shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(16.dp))
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dragging) cs.primaryContainer else cs.surface)
            .clickable { onEdit() }
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = { Store.toggleGoal(g.id) },
            colors = CheckboxDefaults.colors(checkedColor = cs.primary, uncheckedColor = cs.outline)
        )
        Text(g.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            Text(g.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                "Добавлена ${g.createdAt.dateText()}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        IconButton(onClick = { Store.togglePin(g.id) }) {
            Icon(
                if (g.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPinOutlined,
                contentDescription = if (g.pinned) "Открепить" else "Закрепить",
                tint = if (g.pinned) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .pointerInput(g.id) {
                    detectDragGestures(
                        onDragStart = {
                            reorder.draggingId = g.id
                            reorder.offset = 0f
                        },
                        onDragEnd = { reorder.stop() },
                        onDragCancel = { reorder.stop() },
                        onDrag = { change, amount ->
                            change.consume()
                            reorder.onDrag(amount.y, currentGroup.value) { from, to -> Store.moveGoal(from, to) }
                        },
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.DragIndicator, contentDescription = "Перетащить", tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun DoneGoalRow(g: Goal, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceContainer)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = true,
            onCheckedChange = { Store.toggleGoal(g.id) },
            colors = CheckboxDefaults.colors(checkedColor = cs.primary.copy(alpha = 0.6f))
        )
        Text(g.emoji, fontSize = 18.sp, modifier = Modifier.graphicsLayer { alpha = 0.6f })
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            Text(
                g.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.LineThrough,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Добавлена ${g.createdAt.dateText()}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f)
            )
            g.doneAt?.let {
                Text(
                    "Выполнена ${it.dateText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f)
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
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: GOAL_EMOJIS.first()) }
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
                Text("Значок", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GOAL_EMOJIS.forEach { e ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (e == emoji) cs.primaryContainer else cs.surfaceVariant)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(e, fontSize = 20.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, emoji) }, enabled = title.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Удалить", color = cs.error) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
