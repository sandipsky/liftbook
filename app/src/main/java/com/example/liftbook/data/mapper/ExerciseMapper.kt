package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.domain.model.Exercise

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    equipment = equipment,
    type = type,
    isCustom = isCustom,
    isArchived = isArchived,
    createdAt = createdAt,
    defaultRestSeconds = defaultRestSeconds,
    notes = notes,
    // Not a column: a built-in's drawing is fixed data, looked up by its fixed id.
    illustration = ExerciseSeed.illustrationFor(id),
)
