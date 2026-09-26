package com.example.liftbook.domain.usecase

import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeReminderAlarm
import com.example.liftbook.testing.FakeReminderNotifier
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeScheduleRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MutableClock
import com.example.liftbook.testing.exercise
import com.example.liftbook.testing.finishedWorkout
import com.example.liftbook.testing.routine
import com.example.liftbook.testing.routineExercise
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Which reminder goes out, and when the alarm is set for (FR-7.2–7.7). */
class WorkoutRemindersTest {

    /** Saturday 26 September 2026, mid-morning, in UTC. */
    private val clock = MutableClock(Instant.parse("2026-09-26T10:00:00Z"))
    private val bench = exercise("Bench Press")
    private val press = exercise("Overhead Press")
    private val exercises = FakeExerciseRepository(listOf(bench, press))
    private val routines = FakeRoutineRepository(exercises, listOf(routine("Push", routineExercise(bench), routineExercise(press))))
    private val workouts = FakeWorkoutRepository(routines, exercises)
    private val settings = FakeSettingsRepository()
    private val alarm = FakeReminderAlarm()
    private val notifier = FakeReminderNotifier()

    /** Saturdays at 18:00, for Push: reminded at 17:50 by default. */
    private val saturdays = WorkoutSchedule(id = "sat", days = setOf(DayOfWeek.SATURDAY), startTime = LocalTime.of(18, 0), routineId = "push")
    private val schedules = FakeScheduleRepository(listOf(saturdays))

    private val reminders = WorkoutReminders(schedules, routines, workouts, settings, alarm, notifier, clock)

    private fun at(instant: String) {
        clock.instant = Instant.parse(instant)
    }

    @Test
    fun `sync sets the alarm for the next reminder`() = runTest {
        reminders.sync()

        assertEquals(Instant.parse("2026-09-26T17:50:00Z"), alarm.at)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `with reminders switched off, or nothing on, no alarm is set`() = runTest {
        reminders.sync()
        settings.userPreferences.value = UserPreferences(remindersEnabled = false)
        reminders.sync()
        assertNull(alarm.at)

        settings.userPreferences.value = UserPreferences()
        schedules.setEnabled("sat", enabled = false)
        reminders.sync()
        assertNull(alarm.at)
    }

    @Test
    fun `when the alarm goes off, the reminder goes out and the next one is set`() = runTest {
        reminders.sync()
        at("2026-09-26T17:50:00Z")

        reminders.sync()

        val notice = notifier.showing.getValue("sat")
        assertEquals(Instant.parse("2026-09-26T18:00:00Z"), notice.startsAt)
        assertEquals(10, notice.minutesToStart)
        assertEquals("Push", notice.routineName)
        assertEquals(listOf("Bench Press", "Overhead Press"), notice.exerciseNames)
        assertEquals(UserPreferences.DEFAULT_SNOOZE_MINUTES, notice.snoozeMinutes)
        assertEquals(Instant.parse("2026-10-03T17:50:00Z"), alarm.at)
    }

    @Test
    fun `a late alarm still sends its reminder, saying how long is left`() = runTest {
        reminders.sync()
        at("2026-09-26T17:57:30Z")

        reminders.sync()

        assertEquals(3, notifier.showing.getValue("sat").minutesToStart)
    }

    @Test
    fun `nothing is sent twice`() = runTest {
        reminders.sync()
        at("2026-09-26T17:50:00Z")
        reminders.sync()
        at("2026-09-26T17:51:00Z")

        reminders.sync()

        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `syncing before the alarm is due sends nothing`() = runTest {
        reminders.sync()
        at("2026-09-26T12:00:00Z")

        reminders.sync()

        assertTrue(notifier.shown.isEmpty())
        assertEquals(Instant.parse("2026-09-26T17:50:00Z"), alarm.at)
    }

    @Test
    fun `a change made while the alarm is overdue delivers it rather than losing it`() = runTest {
        reminders.sync()
        // An inexact alarm that hasn't gone off yet, and an unrelated setting changes.
        at("2026-09-26T17:53:00Z")
        settings.setSnoozeMinutes(15)

        reminders.sync()

        assertEquals(listOf("sat"), notifier.shown.map { it.scheduleId })
    }

    @Test
    fun `no reminder while a workout is in progress`() = runTest {
        reminders.sync()
        workouts.startEmpty("Morning workout")
        at("2026-09-26T17:50:00Z")

        reminders.sync()

        assertTrue(notifier.shown.isEmpty())
        assertEquals(Instant.parse("2026-10-03T17:50:00Z"), alarm.at)
    }

    @Test
    fun `no reminder once a workout was finished earlier that day`() = runTest {
        reminders.sync()
        workouts.finished.value = mapOf("done" to finishedWorkout("done", "Morning", startedAt = "2026-09-26T07:00:00Z"))
        at("2026-09-26T17:50:00Z")

        reminders.sync()

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a workout finished the day before doesn't hold a reminder back`() = runTest {
        reminders.sync()
        workouts.finished.value = mapOf("done" to finishedWorkout("done", "Friday", startedAt = "2026-09-25T18:00:00Z"))
        at("2026-09-26T17:50:00Z")

        reminders.sync()

        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `snooze brings the reminder back after the snooze length`() = runTest {
        reminders.sync()
        at("2026-09-26T17:50:00Z")
        reminders.sync()

        reminders.snooze("sat")

        assertTrue(notifier.showing.isEmpty())
        assertEquals(Instant.parse("2026-09-26T18:00:00Z"), alarm.at)

        at("2026-09-26T18:00:00Z")
        reminders.sync()

        val again = notifier.showing.getValue("sat")
        assertEquals(Instant.parse("2026-09-26T18:00:00Z"), again.startsAt)
        assertEquals(0, again.minutesToStart)
        assertNull(schedules.get("sat")?.snoozedUntil)
        assertEquals(Instant.parse("2026-10-03T17:50:00Z"), alarm.at)
    }

    @Test
    fun `skip today takes the reminder down, and nothing more comes for that day`() = runTest {
        reminders.sync()
        at("2026-09-26T17:50:00Z")
        reminders.sync()

        reminders.skip("sat", Instant.parse("2026-09-26T18:00:00Z"))

        assertTrue(notifier.showing.isEmpty())
        assertEquals(LocalDate.of(2026, 9, 26), schedules.get("sat")?.skippedOn)
        // Remind later than before: today's would be due again at 17:55, but it's skipped.
        settings.setReminderLeadMinutes(5)
        at("2026-09-26T17:52:00Z")
        reminders.sync()
        assertEquals(Instant.parse("2026-10-03T17:55:00Z"), alarm.at)
    }

    @Test
    fun `a reminder whose routine is gone still comes, without it`() = runTest {
        schedules.schedules.value = listOf(saturdays.copy(routineId = "deleted"))
        reminders.sync()
        at("2026-09-26T17:50:00Z")

        reminders.sync()

        val notice = notifier.showing.getValue("sat")
        assertNull(notice.routineName)
        assertTrue(notice.exerciseNames.isEmpty())
    }
}
