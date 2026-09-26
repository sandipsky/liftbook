package com.example.liftbook.domain.model

import java.time.Duration
import java.time.Instant

/** A workout: finished, or — while [finishedAt] is null — the one in progress (FR-3.1). */
data class Workout(
    val id: String,
    val name: String,
    /** The routine it was started from. Also null once that routine is deleted. */
    val routineId: String?,
    val startedAt: Instant,
    val finishedAt: Instant?,
    /** In order. */
    val exercises: List<WorkoutExercise>,
    val note: String? = null,
    /** The rest period running now, if any (FR-3.5). Only an active workout has one. */
    val rest: RestTimer? = null,
) {
    val isActive: Boolean get() = finishedAt == null
}

data class WorkoutExercise(
    val id: String,
    val exercise: Exercise,
    /** In order. */
    val sets: List<WorkoutSet>,
    val note: String? = null,
    val restSecondsOverride: Int? = null,
)

/**
 * A set as entered so far. Until it's completed any value may still be missing, so each is
 * nullable; which ones apply depends on the exercise type (FR-3.3). A completed set reads as
 * [SetMetrics].
 */
data class WorkoutSet(
    val id: String,
    val setType: SetType,
    val isCompleted: Boolean,
    /** Kilograms. For bodyweight sets, any added weight. */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val completedAt: Instant? = null,
) {
    val values: SetValues get() = SetValues(weightKg, reps, durationSeconds, distanceMeters)
}

/**
 * The values a set holds, any of which may be missing while it's being entered. Stored units
 * only: kilograms, seconds and metres (FR-6.1).
 */
data class SetValues(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
) {
    /** Only what [type] records (FR-3.3): weight and reps, reps, or time and distance. */
    fun applicableTo(type: ExerciseType): SetValues = when (type) {
        ExerciseType.STRENGTH -> SetValues(weightKg = weightKg, reps = reps)
        ExerciseType.BODYWEIGHT -> SetValues(reps = reps)
        ExerciseType.CARDIO -> SetValues(durationSeconds = durationSeconds, distanceMeters = distanceMeters)
    }

    /**
     * What these values record as a completed set of [type], or null if something it needs is
     * missing: weight and at least one rep for strength (a weight of 0 is allowed — an empty
     * machine, say), a rep for bodyweight, a duration for cardio. Distance is optional.
     */
    fun metricsFor(type: ExerciseType): SetMetrics? = when (type) {
        ExerciseType.STRENGTH ->
            if (weightKg != null && weightKg >= 0.0 && reps != null && reps > 0) SetMetrics.Strength(weightKg, reps) else null
        ExerciseType.BODYWEIGHT ->
            reps?.takeIf { it > 0 }?.let { SetMetrics.Bodyweight(reps = it, addedWeightKg = weightKg?.takeIf { w -> w > 0.0 }) }
        ExerciseType.CARDIO ->
            durationSeconds?.takeIf { it > 0 }
                ?.let { SetMetrics.Cardio(durationSeconds = it, distanceMeters = distanceMeters?.takeIf { d -> d > 0.0 }) }
    }

    /** Whether no value that [type] records has been entered. */
    fun isBlankFor(type: ExerciseType): Boolean = applicableTo(type) == SetValues()
}

/** The values a completed set recorded, as a set would hold them. */
fun SetMetrics.toValues(): SetValues = when (this) {
    is SetMetrics.Strength -> SetValues(weightKg = weightKg, reps = reps)
    is SetMetrics.Bodyweight -> SetValues(weightKg = addedWeightKg, reps = reps)
    is SetMetrics.Cardio -> SetValues(durationSeconds = durationSeconds, distanceMeters = distanceMeters)
}

/**
 * A rest period (FR-3.5), kept as the two instants that bound it rather than as a countdown, so
 * a process kill or Doze loses no time (architecture §6.1): what's left is just subtraction.
 */
data class RestTimer(val startedAt: Instant, val endsAt: Instant) {

    val total: Duration get() = Duration.between(startedAt, endsAt)

    /** Time left at [now]; zero once it's over, never negative. */
    fun remaining(now: Instant): Duration = Duration.between(now, endsAt).coerceAtLeast(Duration.ZERO)

    fun isOver(now: Instant): Boolean = !now.isBefore(endsAt)
}

/** What happened when the user asked to start a workout (FR-2.3, FR-3.1). */
sealed interface StartWorkoutResult {
    data class Started(val workoutId: String) : StartWorkoutResult

    /** A workout from this same routine was already in progress; it's the one to go back to. */
    data class Resumed(val workoutId: String) : StartWorkoutResult

    /** Nothing was started: another workout is in progress, and only one can be (FR-3.1). */
    data class OtherWorkoutActive(val workoutId: String, val name: String) : StartWorkoutResult

    data object RoutineNotFound : StartWorkoutResult
}

/**
 * Edits typed into the workout in progress, saved together (FR-3.7). Only what changed is
 * included.
 */
data class WorkoutEdits(
    /** New values by set id. */
    val setValues: Map<String, SetValues> = emptyMap(),
    /** New notes by workout-exercise id; a null note removes it (FR-3.9). */
    val exerciseNotes: Map<String, String?> = emptyMap(),
    /** Whether [workoutNote] is a change to save. */
    val workoutNoteChanged: Boolean = false,
    val workoutNote: String? = null,
) {
    val isEmpty: Boolean get() = setValues.isEmpty() && exerciseNotes.isEmpty() && !workoutNoteChanged
}
