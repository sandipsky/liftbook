package com.example.liftbook.testing

import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.RoutineExercise
import com.example.liftbook.domain.model.SetTarget
import java.time.Instant

fun routine(
    name: String,
    vararg exercises: RoutineExercise,
    id: String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
    lastPerformedAt: Instant? = null,
) = Routine(
    id = id,
    name = name,
    exercises = exercises.toList(),
    lastPerformedAt = lastPerformedAt,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

fun routineExercise(exercise: Exercise, target: SetTarget = SetTarget.defaultFor(exercise.type), id: String = "re-${exercise.id}") =
    RoutineExercise(id = id, exercise = exercise, target = target)
