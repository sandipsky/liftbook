package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.data.local.projection.WorkoutExerciseWithSets
import com.example.liftbook.data.local.projection.WorkoutWithExercises
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet

fun WorkoutWithExercises.toDomain(): Workout = Workout(
    id = workout.id,
    name = workout.name,
    routineId = workout.routineId,
    startedAt = workout.startedAt,
    finishedAt = workout.finishedAt,
    exercises = exercises
        .sortedWith(compareBy({ it.workoutExercise.position }, { it.workoutExercise.id }))
        .map(WorkoutExerciseWithSets::toDomain),
    note = workout.note,
    rest = workout.restStartedAt?.let { startedAt -> workout.restEndsAt?.let { RestTimer(startedAt, it) } },
)

fun WorkoutExerciseWithSets.toDomain(): WorkoutExercise = WorkoutExercise(
    id = workoutExercise.id,
    exercise = exercise.toDomain(),
    sets = sets.sortedWith(compareBy({ it.position }, { it.id })).map(WorkoutSetEntity::toDomain),
    note = workoutExercise.note,
    restSecondsOverride = workoutExercise.restSecondsOverride,
)

fun WorkoutSetEntity.toDomain(): WorkoutSet = WorkoutSet(
    id = id,
    setType = setType,
    isCompleted = isCompleted,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    completedAt = completedAt,
)
