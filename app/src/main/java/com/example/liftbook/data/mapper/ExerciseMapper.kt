package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.ExerciseEntity
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
)
