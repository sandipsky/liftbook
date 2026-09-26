package com.example.liftbook.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WorkoutTimesTest {

    private val zone = ZoneId.of("Europe/London")

    private fun at(date: String, time: String): Instant = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    /** 18:00 to 19:05 on 22 September. */
    private val span = WorkoutSpan(at("2026-09-22", "18:00"), at("2026-09-22", "19:05"))

    @Test
    fun `a new date moves the whole workout`() {
        val moved = WorkoutTimes.withDate(span, LocalDate.parse("2026-09-20"), zone)

        assertEquals(WorkoutSpan(at("2026-09-20", "18:00"), at("2026-09-20", "19:05")), moved)
    }

    @Test
    fun `a new date keeps a workout that ran past midnight just as long`() {
        val late = WorkoutSpan(at("2026-09-22", "23:30"), at("2026-09-23", "00:40"))

        val moved = WorkoutTimes.withDate(late, LocalDate.parse("2026-09-24"), zone)

        assertEquals(WorkoutSpan(at("2026-09-24", "23:30"), at("2026-09-25", "00:40")), moved)
    }

    @Test
    fun `a new start time keeps the end`() {
        val earlier = WorkoutTimes.withStartTime(span, LocalTime.parse("17:30"), zone)

        assertEquals(WorkoutSpan(at("2026-09-22", "17:30"), at("2026-09-22", "19:05")), earlier)
    }

    @Test
    fun `a start after the end keeps the workout's length instead`() {
        val later = WorkoutTimes.withStartTime(span, LocalTime.parse("20:00"), zone)

        assertEquals(WorkoutSpan(at("2026-09-22", "20:00"), at("2026-09-22", "21:05")), later)
    }

    @Test
    fun `an end time is the first one after the start`() {
        assertEquals(
            WorkoutSpan(span.start, at("2026-09-22", "19:30")),
            WorkoutTimes.withEndTime(span, LocalTime.parse("19:30"), zone),
        )
        // Earlier in the day than the start: it ran past midnight.
        assertEquals(
            WorkoutSpan(span.start, at("2026-09-23", "00:15")),
            WorkoutTimes.withEndTime(span, LocalTime.parse("00:15"), zone),
        )
    }

    @Test
    fun `setting the end fixes a workout left running overnight`() {
        val forgotten = WorkoutSpan(at("2026-09-22", "18:00"), at("2026-09-23", "09:12"))

        val fixed = WorkoutTimes.withEndTime(forgotten, LocalTime.parse("19:05"), zone)

        assertEquals(Duration.ofMinutes(65), fixed.duration)
    }

    @Test
    fun `sets move with the start and stay inside the workout`() {
        val moved = WorkoutSpan(span.start.plus(Duration.ofDays(1)), span.end.plus(Duration.ofDays(1)))
        val set = at("2026-09-22", "18:20")

        assertEquals(at("2026-09-23", "18:20"), WorkoutTimes.completedAt(set, span, moved))

        val shortened = WorkoutSpan(span.start, at("2026-09-22", "18:10"))
        assertEquals(shortened.end, WorkoutTimes.completedAt(set, span, shortened))
    }

    @Test
    fun `a set added in the edit counts as done at the end`() {
        assertEquals(span.end, WorkoutTimes.completedAt(null, span, span))
    }
}
