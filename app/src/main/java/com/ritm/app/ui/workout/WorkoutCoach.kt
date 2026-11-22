package com.ritm.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ritm.app.data.Exercise
import com.ritm.app.data.ExerciseSession
import com.ritm.app.data.Store
import com.ritm.app.data.exerciseHistory
import com.ritm.app.data.lineText
import com.ritm.app.data.nextWorkoutWeight
import com.ritm.app.ui.SoftCard
import com.ritm.app.ui.fmtNum
import com.ritm.app.ui.shortText
import java.time.LocalDate

@Composable
internal fun WorkoutCoach(
    date: LocalDate,
    day: Int,
    exercises: List<Exercise>,
    afterWorkout: Boolean = false,
) {
    val logs = Store.logs
    val cs = MaterialTheme.colorScheme
    val candidates = remember(logs, exercises) {
        exercises.filter { it.weight > 0 }.mapNotNull { exercise ->
            exerciseHistory(logs, exercise.id, exercise.name).firstOrNull()?.let { exercise to it }
        }
    }
    val ranked = candidates.map { (exercise, previous) ->
        val sets = if (afterWorkout) exercise.sets else Store.roundsFor(date, exercise)
        val suggested = nextWorkoutWeight(exercise, previous, sets)
        Triple(exercise, previous, suggested)
    }.sortedByDescending { it.third != null }
    var pending by remember(date, day, afterWorkout) { mutableStateOf<Pair<Exercise, Double>?>(null) }

    SoftCard(color = cs.primaryContainer) {
        Text(
            if (afterWorkout) "На следующую тренировку" else "План на сегодня",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = cs.onPrimaryContainer,
        )
        if (ranked.isEmpty()) {
            Text(
                "Запиши тренировку с рабочими весами, чтобы здесь появились сравнения.",
                modifier = Modifier.padding(top = 8.dp),
                color = cs.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ranked.take(2).forEach { (exercise, previous, suggested) ->
            val current = if (afterWorkout) exercise.weight else Store.workoutWeight(date, exercise)
            val changed = current != exercise.weight
            val canChange = !afterWorkout && Store.canSetWorkoutWeight(date, exercise)
            val reps = previous.exercise.sets.mapNotNull { it.reps.toIntOrNull() }.joinToString(" / ")
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, fontWeight = FontWeight.SemiBold, color = cs.onPrimaryContainer)
                Text(
                    "${previous.date.shortText()} · $reps повт.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer,
                )
                Text(
                    when {
                        changed -> "На сегодня · ${fmtNum(current)} кг"
                        suggested != null -> "${fmtNum(current)} → ${fmtNum(suggested)} кг · " +
                            "порог ${exercise.progressionReps} повт. достигнут во всех кругах"
                        else -> "Цель · ${fmtNum(current)} кг × ${exercise.reps}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer,
                )
                if (suggested != null) {
                    Row {
                        if (canChange) {
                            TextButton(onClick = {
                                Store.setWorkoutWeight(date, exercise, if (changed) null else suggested)
                            }) { Text(if (changed) "Вернуть вес" else "Применить сегодня") }
                        }
                        if (afterWorkout || (canChange && !changed && Store.roundsFor(date, exercise) == exercise.sets)) {
                            TextButton(onClick = { pending = exercise to suggested }) { Text("В программу") }
                        }
                    }
                } else if (changed && canChange) {
                    TextButton(onClick = { Store.setWorkoutWeight(date, exercise, null) }) { Text("Вернуть вес") }
                }
            }
        }
    }

    pending?.let { (exercise, weight) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Обновить вес в программе?") },
            text = { Text("${exercise.name} · ${fmtNum(exercise.weight)} → ${fmtNum(weight)} кг") },
            confirmButton = {
                Button(onClick = {
                    if (Store.program[day].orEmpty().contains(exercise) &&
                        (afterWorkout || Store.canSetWorkoutWeight(date, exercise))
                    ) {
                        Store.saveExercise(day, exercise.copy(weight = weight))
                    }
                    pending = null
                }) { Text("Обновить") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } },
        )
    }
}

@Composable
internal fun ExerciseHistoryDialog(name: String, history: List<ExerciseSession>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (history.size > 1) item { ExerciseWeightChart(history) }
                if (history.isEmpty()) item { Text("По этому упражнению пока нет записей.") }
                items(history.size) { index ->
                    val record = history[index]
                    Column {
                        Text(record.date.shortText(), fontWeight = FontWeight.SemiBold)
                        record.exercise.sets.forEachIndexed { setIndex, set ->
                            if (set.reps.isNotBlank()) {
                                Text("Круг ${record.exercise.setRound(setIndex)} · ${set.lineText()}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

@Composable
private fun ExerciseWeightChart(history: List<ExerciseSession>) {
    val points = history.asReversed().mapNotNull { record ->
        record.exercise.sets.mapNotNull { set ->
            set.weight.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        }.maxOrNull()?.let { record.date to it }
    }
    if (points.size < 2) return
    val low = points.minOf { it.second }
    val high = points.maxOf { it.second }
    val firstDay = points.first().first.toEpochDay()
    val daySpan = (points.last().first.toEpochDay() - firstDay).coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    val summary = points.joinToString { (date, weight) -> "${date.shortText()} — ${fmtNum(weight)} кг" }
    Column {
        Text("Максимальный вес за тренировку", style = MaterialTheme.typography.labelLarge)
        Canvas(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 5.dp, vertical = 8.dp)
                .semantics { contentDescription = summary },
        ) {
            val offsets = points.mapIndexed { index, (date, weight) ->
                val x = if (points.first().first == points.last().first) index.toFloat() / points.lastIndex
                else ((date.toEpochDay() - firstDay).toDouble() / daySpan).toFloat()
                val y = if (low == high) 0.5f else ((weight - low) / (high - low)).toFloat()
                Offset(size.width * x, size.height * (1 - y))
            }
            offsets.zipWithNext().forEach { (from, to) -> drawLine(color, from, to, strokeWidth = 2.dp.toPx()) }
            offsets.forEach { drawCircle(color, radius = 3.dp.toPx(), center = it) }
        }
        Text(
            "${fmtNum(points.first().second)} → ${fmtNum(points.last().second)} кг",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
