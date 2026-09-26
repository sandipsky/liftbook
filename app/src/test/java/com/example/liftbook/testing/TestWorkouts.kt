package com.example.liftbook.testing

import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet
import java.time.Instant

/** A finished workout that ran [minutes] from [startedAt]. */
fun finishedWorkout(
    id: String,
    name: String,
    startedAt: String,
    exercises: List<WorkoutExercise> = emptyList(),
    minutes: Long = 60,
    note: String? = null,
): Workout {
    val start = Instant.parse(startedAt)
    return Workout(
        id = id,
        name = name,
        routineId = null,
        startedAt = start,
        finishedAt = start.plusSeconds(minutes * 60),
        exercises = exercises,
        note = note,
    )
}

fun doneExercise(id: String, exercise: Exercise, vararg sets: WorkoutSet, note: String? = null) =
    WorkoutExercise(id = id, exercise = exercise, sets = sets.toList(), note = note)

/** A completed set. */
fun doneSet(
    id: String,
    weightKg: Double? = null,
    reps: Int? = null,
    durationSeconds: Int? = null,
    type: SetType = SetType.NORMAL,
) = WorkoutSet(
    id = id,
    setType = type,
    isCompleted = true,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    completedAt = Instant.EPOCH,
)
