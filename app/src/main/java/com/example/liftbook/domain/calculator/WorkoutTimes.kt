package com.example.liftbook.domain.calculator

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** When a workout started and ended. */
data class WorkoutSpan(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
}

/**
 * How a past workout's times change as they're edited (FR-4.2): a date, a start time and an end
 * time, in the user's zone.
 *
 * - A new date moves the whole workout, keeping how long it lasted.
 * - A new start time keeps the end where it was — unless the end would then come first, when the
 *   workout keeps its length instead.
 * - An end time is the first moment at that time after the start, so a workout can run past
 *   midnight, and one left running overnight is fixed by setting when it really ended.
 */
object WorkoutTimes {

    fun withDate(span: WorkoutSpan, date: LocalDate, zone: ZoneId): WorkoutSpan {
        val start = date.atTime(span.start.atZone(zone).toLocalTime()).atZone(zone).toInstant()
        return WorkoutSpan(start, start.plus(span.duration))
    }

    fun withStartTime(span: WorkoutSpan, time: LocalTime, zone: ZoneId): WorkoutSpan {
        val start = span.start.atZone(zone).toLocalDate().atTime(time).atZone(zone).toInstant()
        val end = if (span.end.isAfter(start)) span.end else start.plus(span.duration)
        return WorkoutSpan(start, end)
    }

    fun withEndTime(span: WorkoutSpan, time: LocalTime, zone: ZoneId): WorkoutSpan {
        val startDate = span.start.atZone(zone).toLocalDate()
        val sameDay = startDate.atTime(time).atZone(zone).toInstant()
        val end = if (sameDay.isAfter(span.start)) sameDay else startDate.plusDays(1).atTime(time).atZone(zone).toInstant()
        return WorkoutSpan(span.start, end)
    }

    /**
     * When a set counts as completed once its workout moves from [from] to [to]: it moves with
     * the start and stays inside the workout. A set added in the edit has no time of its own and
     * takes the end.
     */
    fun completedAt(completedAt: Instant?, from: WorkoutSpan, to: WorkoutSpan): Instant {
        val moved = completedAt?.plus(Duration.between(from.start, to.start)) ?: to.end
        return moved.coerceIn(to.start, to.end)
    }
}
