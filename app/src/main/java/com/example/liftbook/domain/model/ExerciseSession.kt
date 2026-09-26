package com.example.liftbook.domain.model

import java.time.Instant

/**
 * One appearance of an exercise in a finished workout, with the sets completed in it
 * (FR-1.5, FR-4.3). An exercise added twice to the same workout gives two sessions.
 */
data class ExerciseSession(
    val workoutExerciseId: String,
    val workoutId: String,
    val workoutName: String,
    val startedAt: Instant,
    /** Completed sets, in the order they were logged. */
    val sets: List<LoggedSet>,
)

data class LoggedSet(
    val setType: SetType,
    val metrics: SetMetrics,
)
