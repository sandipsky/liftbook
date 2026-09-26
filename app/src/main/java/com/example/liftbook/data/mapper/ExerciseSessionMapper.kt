package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.data.local.projection.ExerciseSessionWithSets
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics

/** Keeps only completed sets, in logging order. */
fun ExerciseSessionWithSets.toDomain(): ExerciseSession = ExerciseSession(
    workoutExerciseId = session.workoutExerciseId,
    workoutId = session.workoutId,
    workoutName = session.workoutName,
    startedAt = session.startedAt,
    sets = sets.asSequence()
        .filter { it.isCompleted }
        .sortedBy { it.position }
        .mapNotNull { set -> set.toMetrics(session.exerciseType)?.let { LoggedSet(set.setType, it) } }
        .toList(),
)

/**
 * Reads the flat set columns the way the exercise type says to (FR-3.3). Returns null when the
 * columns don't fit the type — a completed strength set missing its reps, say — rather than
 * inventing a value.
 */
fun WorkoutSetEntity.toMetrics(type: ExerciseType): SetMetrics? = when (type) {
    ExerciseType.STRENGTH -> if (weightKg != null && reps != null) SetMetrics.Strength(weightKg, reps) else null
    ExerciseType.BODYWEIGHT -> reps?.let { SetMetrics.Bodyweight(reps = it, addedWeightKg = weightKg) }
    ExerciseType.CARDIO -> durationSeconds?.let { SetMetrics.Cardio(durationSeconds = it, distanceMeters = distanceMeters) }
}
