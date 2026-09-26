package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.WorkoutListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TrainingCalendarTest {

    @Test
    fun `weeks start on the given day, with blanks before the first`() {
        // 1 September 2026 is a Tuesday.
        val september = YearMonth.of(2026, 9)

        val mondayFirst = calendarWeeks(september, DayOfWeek.MONDAY)
        val sundayFirst = calendarWeeks(september, DayOfWeek.SUNDAY)

        assertEquals(listOf(null) + (1..6).map { september.atDay(it) }, mondayFirst.first())
        assertEquals(listOf(null, null) + (1..5).map { september.atDay(it) }, sundayFirst.first())
    }

    @Test
    fun `every week is full, and every day of the month appears once`() {
        val august = YearMonth.of(2026, 8)

        val weeks = calendarWeeks(august, DayOfWeek.MONDAY)

        // Starts on a Saturday: it needs six rows.
        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals((1..31).map { august.atDay(it) }, weeks.flatten().filterNotNull())
    }

    @Test
    fun `a month that fits exactly has no blanks`() {
        // February 2026 starts on a Sunday and has 28 days.
        val weeks = calendarWeeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)

        assertEquals(4, weeks.size)
        assertTrue(weeks.flatten().all { it != null })
    }

    @Test
    fun `a month spans its days in the user's zone`() {
        val zone = ZoneId.of("America/New_York")

        val span = YearMonth.of(2026, 9).instantsIn(zone)

        assertEquals(Instant.parse("2026-09-01T04:00:00Z"), span.from)
        assertEquals(Instant.parse("2026-10-01T04:00:00Z"), span.until)
    }

    @Test
    fun `workouts fall on the day they started in the user's zone`() {
        val zone = ZoneId.of("America/New_York")
        // 02:00 UTC on the 23rd is still the evening of the 22nd in New York.
        val late = item("late", "2026-09-23T02:00:00Z")
        val morning = item("morning", "2026-09-23T13:00:00Z")

        val days = listOf(late, morning).byDay(zone)

        assertEquals(listOf(late), days[LocalDate.of(2026, 9, 22)])
        assertEquals(listOf(morning), days[LocalDate.of(2026, 9, 23)])
    }

    @Test
    fun `totals add up time and volume`() {
        val totals = listOf(item("a", "2026-09-01T18:00:00Z", minutes = 60, volumeKg = 1_000.0), item("b", "2026-09-03T18:00:00Z", minutes = 45, volumeKg = 500.0)).totals()

        assertEquals(WorkoutTotals(workouts = 2, durationSeconds = 105 * 60L, volumeKg = 1_500.0), totals)
    }

    private fun item(id: String, startedAt: String, minutes: Long = 60, volumeKg: Double = 0.0): WorkoutListItem {
        val start = Instant.parse(startedAt)
        return WorkoutListItem(
            id = id,
            name = id,
            startedAt = start,
            finishedAt = start.plusSeconds(minutes * 60),
            volumeKg = volumeKg,
            completedSets = 0,
            exerciseNames = emptyList(),
        )
    }
}
