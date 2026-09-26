package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.MuscleSets
import com.example.liftbook.domain.model.WeekSummary
import com.example.liftbook.domain.model.WeeklySummary
import com.example.liftbook.domain.model.WorkoutListItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The first day of the week [date] falls in, when weeks start on [firstDayOfWeek]. */
fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

/**
 * The instants the weekly summary reads, in [zone]: from the start of last week up to the start
 * of next week. Query the workouts and sets in it, then hand them to [weeklySummary].
 */
fun summaryWeeks(today: LocalDate, firstDayOfWeek: DayOfWeek, zone: ZoneId): ClosedOpenInstants {
    val current = weekStart(today, firstDayOfWeek)
    return ClosedOpenInstants(
        from = current.minusWeeks(1).atStartOfDay(zone).toInstant(),
        until = current.plusWeeks(1).atStartOfDay(zone).toInstant(),
    )
}

/**
 * This week and last, side by side (FR-5.3): how many workouts, the volume they lifted, and the
 * working sets each muscle group got. A workout belongs to the week of the day it started on in
 * [zone]; weeks start on [firstDayOfWeek]. [workouts] and [muscleSets] may hold anything — only
 * what falls in the two weeks counts.
 *
 * Sets are counted by their exercise's primary muscle and exclude warm-ups, as volume does
 * (FR-3.10): a warm-up isn't training the muscle in any sense worth counting.
 */
fun weeklySummary(
    workouts: List<WorkoutListItem>,
    muscleSets: List<MuscleSets>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    zone: ZoneId,
): WeeklySummary {
    val current = weekStart(today, firstDayOfWeek)
    fun week(start: LocalDate): WeekSummary {
        val end = start.plusWeeks(1)
        fun inWeek(date: LocalDate) = !date.isBefore(start) && date.isBefore(end)
        val done = workouts.filter { inWeek(it.startedAt.atZone(zone).toLocalDate()) }
        val sets = muscleSets
            .filter { inWeek(it.startedAt.atZone(zone).toLocalDate()) }
            .groupingBy { it.muscle }
            .fold(0) { total, workout -> total + workout.sets }
        return WeekSummary(start = start, workouts = done.size, volumeKg = done.sumOf { it.volumeKg }, setsByMuscle = sets)
    }
    return WeeklySummary(current = week(current), previous = week(current.minusWeeks(1)))
}
