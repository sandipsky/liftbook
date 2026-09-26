package com.example.liftbook.domain.model

import java.time.Instant
import java.time.LocalDate

/*
 * Progress and stats (FR-5). Everything here is worked out from logged sets when it's read;
 * nothing is stored, so an edit to a past workout shows in every chart at once (FR-4.2).
 */

/**
 * One exercise's working sets in one finished workout — warm-ups never included (FR-3.10). An
 * exercise done twice in a workout is one of these, with the sets of both.
 */
data class ExerciseWorkout(
    val workoutId: String,
    val workoutName: String,
    val startedAt: Instant,
    /** In the order they were logged. */
    val sets: List<SetMetrics>,
)

/**
 * What a chart of one exercise can show (FR-5.1). Each type charts what its sets record: weight
 * for strength, reps for bodyweight, time and distance for cardio — see [forType].
 */
enum class ProgressMetric {
    /** The best Epley estimate among the workout's sets. */
    ESTIMATED_ONE_REP_MAX,

    /** The heaviest working set. */
    MAX_WEIGHT,

    /** Σ weight × reps (FR-3.10: working sets only). */
    VOLUME,

    /** The best set's reps. */
    MOST_REPS,

    /** Every working set's reps, added up. */
    TOTAL_REPS,

    /** Time spent, added up over the workout's sets. */
    TOTAL_DURATION,

    /** Distance covered, added up over the workout's sets. */
    TOTAL_DISTANCE;

    companion object {
        /** What a chart of an exercise of [type] offers, the default first. */
        fun forType(type: ExerciseType): List<ProgressMetric> = when (type) {
            ExerciseType.STRENGTH -> listOf(ESTIMATED_ONE_REP_MAX, MAX_WEIGHT, VOLUME)
            ExerciseType.BODYWEIGHT -> listOf(MOST_REPS, TOTAL_REPS)
            ExerciseType.CARDIO -> listOf(TOTAL_DURATION, TOTAL_DISTANCE)
        }
    }
}

/** How far back a chart looks (FR-5.1). */
enum class ProgressRange(private val months: Long?) {
    ONE_MONTH(1),
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    ONE_YEAR(12),
    ALL(null);

    /** The first day in range when it's [today]; null for all time. */
    fun startOn(today: LocalDate): LocalDate? = months?.let(today::minusMonths)
}

/** One value on a chart: a workout's number for a metric, in stored units (kg, s, m). */
data class ProgressPoint(
    val workoutId: String,
    val workoutName: String,
    /** When the workout started — what places it on the chart, so two on one day don't overlap. */
    val startedAt: Instant,
    /** The day it started on, where the user is. */
    val date: LocalDate,
    val value: Double,
)

/** A week of training (FR-5.3). */
data class WeekSummary(
    /** The week's first day. */
    val start: LocalDate,
    val workouts: Int,
    /** Kilograms; working sets only (FR-3.10). */
    val volumeKg: Double,
    /** Completed working sets by the exercise's primary muscle. */
    val setsByMuscle: Map<MuscleGroup, Int>,
) {
    val sets: Int get() = setsByMuscle.values.sum()

    /** The week's last day. */
    val end: LocalDate get() = start.plusDays(6)
}

/** This week and the one before it, side by side (FR-5.3). */
data class WeeklySummary(val current: WeekSummary, val previous: WeekSummary)

/** How many working sets a finished workout gave one muscle group: what weeks are counted from. */
data class MuscleSets(val startedAt: Instant, val muscle: MuscleGroup, val sets: Int)

/** An exercise with finished work to chart, and when it was last done (FR-5.1). */
data class TrainedExercise(
    val exercise: Exercise,
    val lastPerformedAt: Instant,
    /** Finished workouts it has working sets in. */
    val workouts: Int,
)

/** A weigh-in (FR-5.4). There's at most one per day. */
data class BodyWeightEntry(
    val id: String,
    val date: LocalDate,
    /** Kilograms, like every stored weight (FR-6.1). */
    val weightKg: Double,
    val note: String? = null,
)

/** A point on the body-weight trend line: the average of the weigh-ins around [date]. */
data class TrendPoint(val date: LocalDate, val weightKg: Double)
