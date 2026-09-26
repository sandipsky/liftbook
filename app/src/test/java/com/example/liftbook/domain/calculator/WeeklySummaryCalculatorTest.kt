package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.MuscleSets
import com.example.liftbook.domain.model.WorkoutListItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class WeeklySummaryCalculatorTest {

    /** A Saturday. */
    private val today = LocalDate.of(2026, 9, 26)
    private val zone: ZoneId = ZoneOffset.UTC

    private fun workout(id: String, startedAt: String, volumeKg: Double) = WorkoutListItem(
        id = id,
        name = "Push",
        startedAt = Instant.parse(startedAt),
        finishedAt = Instant.parse(startedAt).plusSeconds(3_600),
        volumeKg = volumeKg,
        completedSets = 0,
        exerciseNames = emptyList(),
    )

    private fun sets(startedAt: String, muscle: MuscleGroup, count: Int) = MuscleSets(Instant.parse(startedAt), muscle, count)

    @Test
    fun `a week starts on the chosen day, on or before the date`() {
        assertEquals(LocalDate.of(2026, 9, 21), weekStart(today, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 20), weekStart(today, DayOfWeek.SUNDAY))
        assertEquals(today, weekStart(today, DayOfWeek.SATURDAY))
    }

    @Test
    fun `the span read runs from last week's start to next week's`() {
        val span = summaryWeeks(today, DayOfWeek.MONDAY, zone)

        assertEquals(Instant.parse("2026-09-14T00:00:00Z"), span.from)
        assertEquals(Instant.parse("2026-09-28T00:00:00Z"), span.until)
    }

    @Test
    fun `each week counts its own workouts, volume and sets per muscle`() {
        val workouts = listOf(
            workout("mon", "2026-09-21T07:00:00Z", 5_000.0),
            workout("thu", "2026-09-24T18:00:00Z", 4_000.0),
            workout("last", "2026-09-16T18:00:00Z", 6_500.0),
            workout("older", "2026-09-10T18:00:00Z", 9_999.0),
        )
        val muscleSets = listOf(
            sets("2026-09-21T07:00:00Z", MuscleGroup.CHEST, 9),
            sets("2026-09-24T18:00:00Z", MuscleGroup.CHEST, 3),
            sets("2026-09-24T18:00:00Z", MuscleGroup.BACK, 8),
            sets("2026-09-16T18:00:00Z", MuscleGroup.QUADS, 10),
            sets("2026-09-10T18:00:00Z", MuscleGroup.CHEST, 99),
        )

        val summary = weeklySummary(workouts, muscleSets, today, DayOfWeek.MONDAY, zone)

        assertEquals(LocalDate.of(2026, 9, 21), summary.current.start)
        assertEquals(LocalDate.of(2026, 9, 27), summary.current.end)
        assertEquals(2, summary.current.workouts)
        assertEquals(9_000.0, summary.current.volumeKg, 0.0)
        assertEquals(mapOf(MuscleGroup.CHEST to 12, MuscleGroup.BACK to 8), summary.current.setsByMuscle)
        assertEquals(20, summary.current.sets)

        assertEquals(LocalDate.of(2026, 9, 14), summary.previous.start)
        assertEquals(1, summary.previous.workouts)
        assertEquals(6_500.0, summary.previous.volumeKg, 0.0)
        assertEquals(mapOf(MuscleGroup.QUADS to 10), summary.previous.setsByMuscle)
    }

    @Test
    fun `where the week starts moves a workout between weeks`() {
        // Sunday the 20th: last week when weeks start on Monday, this week when they start on Sunday.
        val sunday = listOf(workout("sun", "2026-09-20T10:00:00Z", 1_000.0))

        assertEquals(0, weeklySummary(sunday, emptyList(), today, DayOfWeek.MONDAY, zone).current.workouts)
        assertEquals(1, weeklySummary(sunday, emptyList(), today, DayOfWeek.SUNDAY, zone).current.workouts)
    }

    @Test
    fun `a workout belongs to the day it started on where the user is`() {
        // 23:30 UTC on Sunday the 20th is already Monday the 21st in Kathmandu.
        val late = listOf(workout("late", "2026-09-20T23:30:00Z", 1_000.0))

        val summary = weeklySummary(late, emptyList(), today, DayOfWeek.MONDAY, ZoneId.of("Asia/Kathmandu"))

        assertEquals(1, summary.current.workouts)
    }

    @Test
    fun `an empty fortnight is two empty weeks`() {
        val summary = weeklySummary(emptyList(), emptyList(), today, DayOfWeek.MONDAY, zone)

        assertEquals(0, summary.current.workouts)
        assertEquals(0, summary.previous.sets)
        assertEquals(emptyMap<MuscleGroup, Int>(), summary.current.setsByMuscle)
    }
}
