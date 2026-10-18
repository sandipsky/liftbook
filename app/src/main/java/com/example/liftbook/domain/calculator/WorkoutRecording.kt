package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutMode

/**
 * A workout recorded after the fact ([WorkoutMode.RECORDED], FR-3.11) has nothing to mark a set
 * done with: a set is done once it's filled in — once it holds what its exercise type records
 * (FR-3.3). This is that reading of the workout, with every filled-in set complete. It's what the
 * screen's progress and records are judged on while recording, and what finishing saves; the
 * sets left blank are dropped then, as sets left undone are in a live workout.
 *
 * A live workout is returned as it is: there, done means marked done.
 */
fun Workout.withRecordedSets(): Workout {
    if (mode != WorkoutMode.RECORDED) return this
    return copy(
        exercises = exercises.map { exercise ->
            exercise.copy(
                sets = exercise.sets.map { set ->
                    val filled = set.values.metricsFor(exercise.exercise.type) != null
                    if (set.isCompleted == filled) set else set.copy(isCompleted = filled)
                },
            )
        },
    )
}
