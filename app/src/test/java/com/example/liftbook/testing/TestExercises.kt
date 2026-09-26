package com.example.liftbook.testing

import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import java.time.Instant

fun exercise(
    name: String,
    muscle: MuscleGroup = MuscleGroup.CHEST,
    equipment: Equipment = Equipment.BARBELL,
    type: ExerciseType = ExerciseType.STRENGTH,
    id: String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
    isCustom: Boolean = false,
    isArchived: Boolean = false,
) = Exercise(
    id = id,
    name = name,
    primaryMuscle = muscle,
    equipment = equipment,
    type = type,
    isCustom = isCustom,
    isArchived = isArchived,
    createdAt = Instant.EPOCH,
)
