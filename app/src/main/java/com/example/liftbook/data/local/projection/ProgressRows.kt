package com.example.liftbook.data.local.projection

import androidx.room.Embedded
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import java.time.Instant

/** A working set of an exercise, with the finished workout it was part of (FR-5.1). */
data class ProgressSetRow(
    @Embedded val set: WorkoutSetEntity,
    /** How to read the set columns. */
    val exerciseType: ExerciseType,
    val workoutName: String,
    val workoutStartedAt: Instant,
)

/** One finished workout's working sets on one muscle group (FR-5.3). */
data class MuscleSetsRow(
    val startedAt: Instant,
    val muscle: MuscleGroup,
    val sets: Int,
)

/** An exercise with finished working sets, and when it was last done. */
data class TrainedExerciseRow(
    @Embedded val exercise: ExerciseEntity,
    val lastPerformedAt: Instant,
    val workouts: Int,
)
