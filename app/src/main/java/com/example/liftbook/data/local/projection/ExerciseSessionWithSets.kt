package com.example.liftbook.data.local.projection

import androidx.room.Embedded
import androidx.room.Relation
import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.domain.model.ExerciseType
import java.time.Instant

/** One workout-exercise row joined with its workout, plus every set logged under it. */
data class ExerciseSessionWithSets(
    @Embedded val session: ExerciseSessionRow,
    @Relation(parentColumn = "workoutExerciseId", entityColumn = "workoutExerciseId")
    val sets: List<WorkoutSetEntity>,
)

data class ExerciseSessionRow(
    val workoutExerciseId: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: Instant,
    /** How to read the set columns: weight × reps, reps, or duration. */
    val exerciseType: ExerciseType,
)
