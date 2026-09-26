package com.example.liftbook.data.local.projection

import androidx.room.Embedded
import androidx.room.Relation
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutEntity
import com.example.liftbook.data.local.entity.WorkoutExerciseEntity

/**
 * A finished workout as the history lists it: its totals, worked out by the query, and its
 * exercises (unordered — sort by position) for their names (FR-4.1).
 */
data class WorkoutListRow(
    @Embedded val workout: WorkoutEntity,
    val volumeKg: Double,
    val completedSets: Int,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseWithExercise>,
)

data class WorkoutExerciseWithExercise(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)
