package com.example.liftbook.domain.model

/**
 * A personal record set in a workout (FR-5.2), found by comparing it with every earlier
 * workout. Records are derived on read, never stored, so editing history can't leave a stale
 * one behind (architecture §2.9).
 */
sealed interface PersonalRecord {

    /** More weight than ever before, in a working set. */
    data class HeaviestWeight(val weightKg: Double, val reps: Int) : PersonalRecord

    /**
     * More reps than ever before at this weight. For a bodyweight exercise the weight is any
     * added weight, or 0 without.
     */
    data class MostReps(val reps: Int, val weightKg: Double) : PersonalRecord

    /** A higher estimated one-rep max (Epley) than ever before, and the set that earned it. */
    data class BestEstimatedOneRepMax(val estimatedKg: Double, val weightKg: Double, val reps: Int) : PersonalRecord
}

/** An exercise's new records from one workout, in the order the workout did the exercises. */
data class ExerciseRecords(
    val exercise: Exercise,
    val records: List<PersonalRecord>,
)

/** What a finished workout adds up to (FR-3.8). */
data class WorkoutSummary(
    val durationSeconds: Long,
    /** Kilograms; working sets only (FR-3.10). */
    val volumeKg: Double,
    val completedSets: Int,
    val records: List<ExerciseRecords>,
    /** The sets that set [records], by id, to flag where they're listed. */
    val recordSetIds: Set<String> = emptySet(),
)
