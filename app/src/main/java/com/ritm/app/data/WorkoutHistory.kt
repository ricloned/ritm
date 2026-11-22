package com.ritm.app.data

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

internal data class ExerciseSession(val date: LocalDate, val exercise: ExerciseLog)

internal fun exerciseHistory(
    logs: List<WorkoutLog>,
    exerciseId: Long,
    name: String,
    before: WorkoutLog? = null,
): List<ExerciseSession> = logs.asSequence()
    .filter { before == null || it.date < before.date || (it.date == before.date && it.finishedAt < before.finishedAt) }
    .sortedWith(compareByDescending<WorkoutLog> { it.date }.thenByDescending { it.finishedAt })
    .mapNotNull { log ->
        // Names also link the same exercise entered separately on different days of the program.
        val match = log.exercises.firstOrNull { exerciseId != 0L && it.exerciseId == exerciseId }
            ?: log.exercises.singleOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
        match?.takeIf { it.sets.any { set -> set.reps.toIntOrNull()?.let { reps -> reps > 0 } == true } }
            ?.let { ExerciseSession(log.date, it) }
    }
    .take(5)
    .toList()

internal fun exerciseHistorySummary(logs: List<WorkoutLog>, workout: WorkoutLog): String =
    workout.exercises.joinToString("\n") { exercise ->
        val history = exerciseHistory(logs, exercise.exerciseId, exercise.name, before = workout)
        val sessions = history.joinToString(" · ") { previous ->
            val details = previous.exercise.sets.mapIndexedNotNull { index, set ->
                set.takeIf { it.reps.isNotBlank() }?.let {
                    "круг ${previous.exercise.setRound(index)}: ${it.lineText()}"
                }
            }.joinToString(", ")
            "${previous.date} — $details"
        }
        "${exercise.name}: ${sessions.ifBlank { "истории пока нет" }}"
    }

internal fun nextWorkoutWeight(exercise: Exercise, session: ExerciseSession, plannedSets: Int): Double? {
    if (exercise.weight <= 0 || exercise.weightStep <= 0 || exercise.progressionReps <= 0) return null
    val previous = session.exercise
    if (previous.plannedSets != plannedSets || previous.sets.size != plannedSets) return null
    val allReady = previous.sets.indices.all { index ->
        val set = previous.sets[index]
        val weight = set.weight.replace(',', '.').toDoubleOrNull()
        previous.setRound(index) == index + 1 &&
            (set.reps.toIntOrNull() ?: 0) >= exercise.progressionReps &&
            weight != null && abs(weight - exercise.weight) < 0.00001
    }
    return (exercise.weight + exercise.weightStep).takeIf { allReady && it.isFinite() }
}

internal fun WorkoutLog.exerciseNames(): Set<String> = exercises
    .filter { it.sets.any { set -> set.reps.isNotBlank() } }
    .mapTo(mutableSetOf()) { it.name.trim().lowercase(Locale.ROOT) }
