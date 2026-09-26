package com.example.liftbook.data.local.projection

import androidx.room.Embedded
import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.domain.model.ExerciseType

/** A set, with the type of its exercise to read its columns by. */
data class SetWithExerciseType(
    @Embedded val set: WorkoutSetEntity,
    val exerciseType: ExerciseType,
)
