package com.example.liftbook.domain.model

import java.time.Instant

/**
 * A routine: a named, ordered list of exercises with targets, that a workout can start from
 * (FR-2.1, FR-2.3).
 */
data class Routine(
    val id: String,
    val name: String,
    /** In the order they're done. */
    val exercises: List<RoutineExercise>,
    /**
     * When the last finished workout started from this routine began, or null if there is none
     * (FR-2.4). Derived from the workouts, never stored, so editing history keeps it right.
     */
    val lastPerformedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val notes: String? = null,
)

/** One exercise in a routine, with the target each of its sets starts from. */
data class RoutineExercise(
    val id: String,
    val exercise: Exercise,
    val target: SetTarget,
    val restSecondsOverride: Int? = null,
    val notes: String? = null,
)

/**
 * What each set of an exercise aims for: a number of sets, each pre-filled with the same values.
 *
 * Flat rather than sealed like [SetMetrics]: which values apply is decided by the exercise type
 * when the target is read, because a custom exercise's type can still change while no sets are
 * logged against it. [applicableTo] keeps only the values a type records.
 */
data class SetTarget(
    val sets: Int,
    val reps: Int? = null,
    /** Kilograms, like every stored weight (FR-6.1). */
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
) {
    /** Only what [type] records (FR-3.3): weight and reps, reps, or time and distance. */
    fun applicableTo(type: ExerciseType): SetTarget = when (type) {
        ExerciseType.STRENGTH -> SetTarget(sets, reps = reps, weightKg = weightKg)
        ExerciseType.BODYWEIGHT -> SetTarget(sets, reps = reps)
        ExerciseType.CARDIO -> SetTarget(sets, durationSeconds = durationSeconds, distanceMeters = distanceMeters)
    }

    companion object {
        const val MIN_SETS = 1
        const val MAX_SETS = 20

        /** What a newly added exercise starts with: 3 × 10, or a single set of cardio. */
        fun defaultFor(type: ExerciseType): SetTarget = when (type) {
            ExerciseType.STRENGTH, ExerciseType.BODYWEIGHT -> SetTarget(sets = 3, reps = 10)
            ExerciseType.CARDIO -> SetTarget(sets = 1)
        }
    }
}

/** What the routine editor saves (FR-2.1, FR-2.2). */
data class RoutineDraft(
    val name: String,
    /** In order: the position of each exercise is its index. */
    val exercises: List<RoutineExerciseDraft>,
)

data class RoutineExerciseDraft(
    val exerciseId: String,
    val target: SetTarget,
    /** The routine exercise this was loaded from, so saving keeps its row; null when newly added. */
    val id: String? = null,
)
