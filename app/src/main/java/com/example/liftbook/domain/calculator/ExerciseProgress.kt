package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.ExerciseWorkout
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressPoint
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import java.time.LocalDate
import java.time.ZoneId

/**
 * A workout's number for this metric, from its working sets (FR-5.1), in stored units: kilograms,
 * reps, seconds or metres. Null when the sets have nothing to show for it — a cardio workout has
 * no 1RM, one with no distance logged has no distance — so the chart leaves the workout out
 * rather than plotting a zero that never happened.
 */
fun ProgressMetric.valueFor(sets: List<SetMetrics>): Double? {
    val strength = sets.filterIsInstance<SetMetrics.Strength>()
    val reps = sets.mapNotNull { set ->
        when (set) {
            is SetMetrics.Strength -> set.reps
            is SetMetrics.Bodyweight -> set.reps
            is SetMetrics.Cardio -> null
        }
    }
    val cardio = sets.filterIsInstance<SetMetrics.Cardio>()
    return when (this) {
        ProgressMetric.ESTIMATED_ONE_REP_MAX -> strength.maxOfOrNull { oneRepMax(it.weightKg, it.reps) }
        ProgressMetric.MAX_WEIGHT -> strength.maxOfOrNull { it.weightKg }
        // The sets are working sets already; volume() counts a bodyweight set's added weight only.
        ProgressMetric.VOLUME -> if (reps.isEmpty()) null else volume(sets.map { LoggedSet(SetType.NORMAL, it) })
        ProgressMetric.MOST_REPS -> reps.maxOrNull()?.toDouble()
        ProgressMetric.TOTAL_REPS -> if (reps.isEmpty()) null else reps.sum().toDouble()
        ProgressMetric.TOTAL_DURATION -> if (cardio.isEmpty()) null else cardio.sumOf { it.durationSeconds }.toDouble()
        ProgressMetric.TOTAL_DISTANCE -> cardio.mapNotNull { it.distanceMeters }.takeIf { it.isNotEmpty() }?.sum()
    }
}

/**
 * One exercise's [metric] over [range], one point per workout, oldest first (FR-5.1). A
 * workout's day is the one it started on in [zone]; [range] counts back from [today].
 */
fun progressSeries(
    workouts: List<ExerciseWorkout>,
    metric: ProgressMetric,
    range: ProgressRange,
    today: LocalDate,
    zone: ZoneId,
): List<ProgressPoint> {
    val start = range.startOn(today)
    return workouts
        .sortedBy { it.startedAt }
        .mapNotNull { workout ->
            val date = workout.startedAt.atZone(zone).toLocalDate()
            if (start != null && date.isBefore(start)) return@mapNotNull null
            metric.valueFor(workout.sets)?.let { ProgressPoint(workout.workoutId, workout.workoutName, workout.startedAt, date, it) }
        }
}

/** How a series moved: where it started and ended in its range, and its best. */
data class SeriesSummary(
    val first: ProgressPoint,
    val latest: ProgressPoint,
    val best: ProgressPoint,
) {
    /** From the first point to the latest; 0 with a single point. */
    val change: Double get() = latest.value - first.value
}

/** Null for an empty series. For every metric, higher is better; the earliest best wins a tie. */
fun List<ProgressPoint>.summary(): SeriesSummary? {
    if (isEmpty()) return null
    return SeriesSummary(first = first(), latest = last(), best = maxBy { it.value })
}
