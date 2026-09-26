package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.local.projection.MuscleSetsRow
import com.example.liftbook.data.local.projection.ProgressSetRow
import com.example.liftbook.data.local.projection.TrainedExerciseRow
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.ExerciseWorkout
import com.example.liftbook.domain.model.MuscleSets
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.TrainedExercise

/**
 * Rows in workout order, grouped into one entry per workout. A set whose columns don't fit the
 * exercise type is skipped rather than guessed at; a workout left with none is left out.
 */
fun List<ProgressSetRow>.toExerciseWorkouts(): List<ExerciseWorkout> {
    val workouts = ArrayList<ExerciseWorkout>()
    var current: ProgressSetRow? = null
    val sets = ArrayList<SetMetrics>()
    fun flush() {
        val row = current ?: return
        if (sets.isNotEmpty()) workouts += ExerciseWorkout(row.set.workoutId, row.workoutName, row.workoutStartedAt, sets.toList())
        sets.clear()
    }
    forEach { row ->
        if (row.set.workoutId != current?.set?.workoutId) {
            flush()
            current = row
        }
        row.set.toMetrics(row.exerciseType)?.let(sets::add)
    }
    flush()
    return workouts
}

fun MuscleSetsRow.toDomain(): MuscleSets = MuscleSets(startedAt = startedAt, muscle = muscle, sets = sets)

fun TrainedExerciseRow.toDomain(): TrainedExercise =
    TrainedExercise(exercise = exercise.toDomain(), lastPerformedAt = lastPerformedAt, workouts = workouts)

fun BodyWeightEntryEntity.toDomain(): BodyWeightEntry =
    BodyWeightEntry(id = id, date = recordedOn, weightKg = weightKg, note = note)
