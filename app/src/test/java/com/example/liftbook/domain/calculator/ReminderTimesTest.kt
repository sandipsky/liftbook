package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.WorkoutSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderTimesTest {

    private val utc = ZoneOffset.UTC

    /** Saturday 26 September 2026, mid-morning. */
    private val saturday = Instant.parse("2026-09-26T10:00:00Z")

    private fun entry(
        vararg days: DayOfWeek,
        at: LocalTime = LocalTime.of(18, 0),
        leadMinutes: Int? = null,
        isEnabled: Boolean = true,
        snoozedUntil: Instant? = null,
        skippedOn: LocalDate? = null,
    ) = WorkoutSchedule(
        id = "entry",
        days = days.toSet(),
        startTime = at,
        routineName = "Push",
        leadMinutes = leadMinutes,
        isEnabled = isEnabled,
        snoozedUntil = snoozedUntil,
        skippedOn = skippedOn,
    )

    private fun next(vararg schedules: WorkoutSchedule, after: Instant = saturday, zone: ZoneId = utc, defaultLead: Int = 10) =
        ReminderTimes.nextAfter(schedules.toList(), after, zone, defaultLead)

    @Test
    fun `a reminder comes the default lead time before the start`() {
        assertEquals(Instant.parse("2026-09-28T17:50:00Z"), next(entry(DayOfWeek.MONDAY)))
    }

    @Test
    fun `an entry's own lead time wins over the default`() {
        assertEquals(Instant.parse("2026-09-28T17:30:00Z"), next(entry(DayOfWeek.MONDAY, leadMinutes = 30)))
        assertEquals(Instant.parse("2026-09-28T18:00:00Z"), next(entry(DayOfWeek.MONDAY, leadMinutes = 0)))
    }

    @Test
    fun `today's reminder is next until it has come`() {
        assertEquals(Instant.parse("2026-09-26T17:50:00Z"), next(entry(DayOfWeek.SATURDAY)))
        assertEquals(Instant.parse("2026-10-03T17:50:00Z"), next(entry(DayOfWeek.SATURDAY), after = Instant.parse("2026-09-26T17:50:00Z")))
    }

    @Test
    fun `of several days and entries, the soonest reminder is next`() {
        val weekdays = entry(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val sunday = entry(DayOfWeek.SUNDAY, at = LocalTime.of(9, 0)).copy(id = "sunday")

        assertEquals(Instant.parse("2026-09-27T08:50:00Z"), next(weekdays, sunday))
    }

    @Test
    fun `entries that are off don't remind`() {
        assertNull(next(entry(DayOfWeek.SATURDAY, isEnabled = false, snoozedUntil = Instant.parse("2026-09-26T12:00:00Z"))))
        assertNull(next())
    }

    @Test
    fun `a skipped day is passed over`() {
        val skipped = entry(DayOfWeek.SATURDAY, skippedOn = LocalDate.of(2026, 9, 26))

        assertEquals(Instant.parse("2026-10-03T17:50:00Z"), next(skipped))
    }

    @Test
    fun `a snooze comes back when asked, ahead of the next regular reminder`() {
        val snoozed = entry(DayOfWeek.SATURDAY, snoozedUntil = Instant.parse("2026-09-26T18:05:00Z"))

        assertEquals(Instant.parse("2026-09-26T18:05:00Z"), next(snoozed, after = Instant.parse("2026-09-26T17:56:00Z")))
    }

    @Test
    fun `a lead time can reach back into the day before`() {
        val justAfterMidnight = entry(DayOfWeek.MONDAY, at = LocalTime.of(0, 5))

        assertEquals(Instant.parse("2026-09-27T23:55:00Z"), next(justAfterMidnight))
    }

    @Test
    fun `due gives what came due in the window, ends included`() {
        val schedule = entry(DayOfWeek.SATURDAY)

        val atStart = ReminderTimes.due(listOf(schedule), Instant.parse("2026-09-26T17:50:00Z"), Instant.parse("2026-09-26T17:50:00Z"), utc, 10)
        val before = ReminderTimes.due(listOf(schedule), saturday, Instant.parse("2026-09-26T17:49:59Z"), utc, 10)

        assertEquals(listOf(Instant.parse("2026-09-26T18:00:00Z")), atStart.map { it.startsAt })
        assertEquals(listOf(Instant.parse("2026-09-26T17:50:00Z")), atStart.map { it.remindAt })
        assertTrue(before.isEmpty())
    }

    @Test
    fun `due gives one reminder per entry, the latest, however long the window`() {
        val schedule = entry(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

        val due = ReminderTimes.due(listOf(schedule), Instant.parse("2026-09-25T00:00:00Z"), Instant.parse("2026-09-26T18:30:00Z"), utc, 10)

        assertEquals(listOf(Instant.parse("2026-09-26T18:00:00Z")), due.map { it.startsAt })
    }

    @Test
    fun `a reminder over an hour after its start is dropped`() {
        val schedule = entry(DayOfWeek.SATURDAY)
        val from = Instant.parse("2026-09-26T17:50:00Z")

        assertEquals(1, ReminderTimes.due(listOf(schedule), from, Instant.parse("2026-09-26T18:59:59Z"), utc, 10).size)
        assertTrue(ReminderTimes.due(listOf(schedule), from, Instant.parse("2026-09-26T19:00:00Z"), utc, 10).isEmpty())
    }

    @Test
    fun `a snooze belongs to the workout it put off, however late`() {
        val snoozed = entry(DayOfWeek.SATURDAY, snoozedUntil = Instant.parse("2026-09-26T20:10:00Z"))

        val due = ReminderTimes.due(listOf(snoozed), Instant.parse("2026-09-26T20:00:00Z"), Instant.parse("2026-09-26T20:10:00Z"), utc, 10)

        val reminder = due.single()
        assertTrue(reminder.isSnooze)
        assertEquals(Instant.parse("2026-09-26T18:00:00Z"), reminder.startsAt)
    }

    @Test
    fun `times are local, through a change of the clocks`() {
        val london = ZoneId.of("Europe/London")
        val monday = entry(DayOfWeek.MONDAY)

        // British Summer Time, then Greenwich Mean Time from Sunday 25 October.
        assertEquals(Instant.parse("2026-10-19T16:50:00Z"), next(monday, after = Instant.parse("2026-10-17T12:00:00Z"), zone = london))
        assertEquals(Instant.parse("2026-10-26T17:50:00Z"), next(monday, after = Instant.parse("2026-10-24T12:00:00Z"), zone = london))
    }

    @Test
    fun `a start in the hour the clocks skip moves to just after it`() {
        val london = ZoneId.of("Europe/London")
        // 01:30 doesn't exist on Sunday 29 March 2026: the clocks go from 01:00 to 02:00.
        val sunday = entry(DayOfWeek.SUNDAY, at = LocalTime.of(1, 30))

        val (_, startsAt) = ReminderTimes.nextStart(listOf(sunday), Instant.parse("2026-03-28T12:00:00Z"), london)!!

        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), startsAt)
    }

    @Test
    fun `the next workout to start, and the week's count, leave out entries that are off`() {
        val on = entry(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        val off = entry(DayOfWeek.SUNDAY, at = LocalTime.of(9, 0), isEnabled = false).copy(id = "off")

        val (schedule, startsAt) = ReminderTimes.nextStart(listOf(on, off), saturday, utc)!!

        assertEquals("entry", schedule.id)
        assertEquals(Instant.parse("2026-09-28T18:00:00Z"), startsAt)
        assertEquals(2, ReminderTimes.perWeek(listOf(on, off)))
    }
}
