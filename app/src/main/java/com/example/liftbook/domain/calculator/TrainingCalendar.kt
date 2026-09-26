package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.WorkoutListItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A month of the training calendar (FR-4.4) as weeks of seven days, each week starting on
 * [firstDayOfWeek]. Days before the 1st and after the last day are null, so every week is full.
 */
fun calendarWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
    val leading = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDayOfWeek.value, DAYS_PER_WEEK)
    val days: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    return days.chunked(DAYS_PER_WEEK) { week -> week + List(DAYS_PER_WEEK - week.size) { null } }
}

/** The instants [month] covers in [zone]: from its first midnight up to the next month's. */
fun YearMonth.instantsIn(zone: ZoneId): ClosedOpenInstants =
    ClosedOpenInstants(atDay(1).atStartOfDay(zone).toInstant(), plusMonths(1).atDay(1).atStartOfDay(zone).toInstant())

data class ClosedOpenInstants(val from: Instant, val until: Instant)

/** Workouts by the day they started on in [zone], each day's in the order given. */
fun List<WorkoutListItem>.byDay(zone: ZoneId): Map<LocalDate, List<WorkoutListItem>> =
    groupBy { it.startedAt.atZone(zone).toLocalDate() }

/** What a run of workouts adds up to, such as a month's under the calendar. */
data class WorkoutTotals(
    val workouts: Int,
    val durationSeconds: Long,
    /** Kilograms; working sets only (FR-3.10). */
    val volumeKg: Double,
)

fun List<WorkoutListItem>.totals(): WorkoutTotals =
    WorkoutTotals(workouts = size, durationSeconds = sumOf { it.durationSeconds }, volumeKg = sumOf { it.volumeKg })

private const val DAYS_PER_WEEK = 7
