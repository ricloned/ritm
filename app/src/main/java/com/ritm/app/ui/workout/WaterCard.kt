package com.ritm.app.ui.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritm.app.data.Store
import com.ritm.app.ui.timeText
import java.time.LocalDate

private val WATER = Color(0xFF4FA3E0)
private val WATER_LIGHT = Color(0xFF4FA3E0).copy(alpha = 0.16f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterCard(date: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val entries = Store.water.filter { it.date == date }.sortedBy { it.time }
    val total = entries.sumOf { it.ml }
    val goal = Store.settings.waterGoal
    val progress by animateFloatAsState((total.toFloat() / goal).coerceIn(0f, 1f), tween(600), label = "water")
    var custom by remember { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(WATER_LIGHT),
                contentAlignment = Alignment.Center
            ) { Text("💧", fontSize = 18.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Вода", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$total",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = WATER
                    )
                    Text(
                        " / $goal мл",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { editGoal = true }
                    )
                }
            }
            Text(
                if (total >= goal) "норма ✓" else "ещё ${goal - total} мл",
                style = MaterialTheme.typography.labelLarge,
                color = if (total >= goal) WATER else cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(WATER_LIGHT)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(WATER)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(200, 300, 500).forEach { ml ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WATER_LIGHT)
                        .clickable { Store.addWater(date, ml) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+$ml", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = WATER)
                }
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(WATER)
                    .clickable { custom = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Add, "Своё количество", tint = Color.White) }
        }
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.forEach { w ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cs.surfaceVariant)
                            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${w.time.timeText()} · ${w.ml} мл",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Удалить",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { Store.deleteWater(w.id) }
                                .padding(3.dp)
                        )
                    }
                }
            }
        }
    }

    if (custom) {
        MlDialog(
            title = "Сколько выпил?",
            initial = "",
            onDismiss = { custom = false },
        ) { ml ->
            Store.addWater(date, ml)
            custom = false
        }
    }
    if (editGoal) {
        MlDialog(
            title = "Норма воды в день",
            initial = goal.toString(),
            hint = "Ориентир: 30–35 мл на кг веса, в дни тренировок — больше.",
            onDismiss = { editGoal = false },
        ) { ml ->
            Store.updateSettings(Store.settings.copy(waterGoal = ml.coerceIn(500, 8000)))
            editGoal = false
        }
    }
}

@Composable
private fun MlDialog(
    title: String,
    initial: String,
    hint: String? = null,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                    label = { Text("мл") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { text.toIntOrNull()?.let(onSave) },
                enabled = (text.toIntOrNull() ?: 0) > 0
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
