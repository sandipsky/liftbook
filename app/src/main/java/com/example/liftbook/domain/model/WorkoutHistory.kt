package com.example.liftbook.domain.model

import java.time.Duration
import java.time.Instant

/**
 * A finished workout as the history shows it (FR-4.1, FR-4.4): when it was, what was done, and
 * what it added up to. The totals come from its logged sets, so an edit changes them (FR-4.2).
 */
data class WorkoutListItem(
    val id: String,
    val name: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    /** Kilograms; completed working sets only (FR-3.10). */
    val volumeKg: Double,
    /** Every completed set, warm-ups included, as the summary counts them. */
    val completedSets: Int,
    /** In workout order. */
    val exerciseNames: List<String>,
) {
    val durationSeconds: Long get() = Duration.between(startedAt, finishedAt).seconds.coerceAtLeast(0)
}

/**
 * A finished workout as edited (FR-4.2), saved as a whole. Kept rows keep their ids and are
 * updated in place; an id the workout doesn't have yet is a new row. A finished workout holds
 * only what was done, so every set is complete and reads as its [SetMetrics].
 */
data class WorkoutRevision(
    val name: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    val note: String?,
    /** In order. */
    val exercises: List<ExerciseRevision>,
)

data class ExerciseRevision(
    /** The workout-exercise id. */
    val id: String,
    val exerciseId: String,
    val note: String?,
    /** In order. */
    val sets: List<SetRevision>,
)

data class SetRevision(
    val id: String,
    val setType: SetType,
    val metrics: SetMetrics,
)
