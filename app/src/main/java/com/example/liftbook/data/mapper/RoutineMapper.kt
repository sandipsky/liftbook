package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.RoutineExerciseEntity
import com.example.liftbook.data.local.projection.RoutineExerciseWithExercise
import com.example.liftbook.data.local.projection.RoutineWithExercises
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.RoutineExercise
import com.example.liftbook.domain.model.RoutineExerciseDraft
import com.example.liftbook.domain.model.SetTarget

fun RoutineWithExercises.toDomain(): Routine = Routine(
    id = routine.id,
    name = routine.name,
    exercises = exercises
        .sortedWith(compareBy({ it.routineExercise.position }, { it.routineExercise.id }))
        .map(RoutineExerciseWithExercise::toDomain),
    lastPerformedAt = lastPerformedAt,
    createdAt = routine.createdAt,
    updatedAt = routine.updatedAt,
    notes = routine.notes,
)

fun RoutineExerciseWithExercise.toDomain(): RoutineExercise = RoutineExercise(
    id = routineExercise.id,
    exercise = exercise.toDomain(),
    target = routineExercise.toTarget(),
    restSecondsOverride = routineExercise.restSecondsOverride,
    notes = routineExercise.notes,
)

fun RoutineExerciseEntity.toTarget(): SetTarget = SetTarget(
    sets = targetSets,
    reps = targetReps,
    weightKg = targetWeightKg,
    durationSeconds = targetDurationSeconds,
    distanceMeters = targetDistanceMeters,
)

/** A new row for [draft]; rest time and notes aren't edited yet, so they start empty. */
fun RoutineExerciseDraft.toEntity(id: String, routineId: String, position: Int): RoutineExerciseEntity =
    RoutineExerciseEntity(
        id = id,
        routineId = routineId,
        exerciseId = exerciseId,
        position = position,
        targetSets = target.sets,
        targetReps = target.reps,
        targetWeightKg = target.weightKg,
        targetDurationSeconds = target.durationSeconds,
        targetDistanceMeters = target.distanceMeters,
        restSecondsOverride = null,
        notes = null,
    )

/** [this] row updated to [draft] at [position], keeping what the draft doesn't carry. */
fun RoutineExerciseEntity.updatedTo(draft: RoutineExerciseDraft, position: Int): RoutineExerciseEntity = copy(
    exerciseId = draft.exerciseId,
    position = position,
    targetSets = draft.target.sets,
    targetReps = draft.target.reps,
    targetWeightKg = draft.target.weightKg,
    targetDurationSeconds = draft.target.durationSeconds,
    targetDistanceMeters = draft.target.distanceMeters,
)
