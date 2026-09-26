package com.example.liftbook.ui.feature.reminders

import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.testing.FakeReminderAlarm
import com.example.liftbook.testing.FakeScheduleRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.MutableClock
import com.example.liftbook.testing.testReminders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Saturday 26 September 2026, mid-morning. */
    private val clock = MutableClock(Instant.parse("2026-09-26T10:00:00Z"))
    private val weekdays = WorkoutSchedule(
        id = "weekdays",
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        startTime = LocalTime.of(7, 0),
        routineId = "push",
    )
    private val saturday = WorkoutSchedule(id = "saturday", days = setOf(DayOfWeek.SATURDAY), startTime = LocalTime.of(18, 0))
    private val schedules = FakeScheduleRepository(listOf(weekdays, saturday)).apply { routineNames = mapOf("push" to "Push") }
    private val settings = FakeSettingsRepository()
    private val alarm = FakeReminderAlarm()
    private val events = mutableListOf<ReminderListEvent>()

    private fun TestScope.reminderList(): ReminderListViewModel =
        ReminderListViewModel(schedules, settings, testReminders(schedules = schedules, settings = settings, alarm = alarm, clock = clock), clock)
            .also { viewModel ->
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
            }

    @Test
    fun `shows the schedule, the week's count and the next workout`() = runTest {
        val state = reminderList().uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("weekdays", "saturday"), state.schedules.map { it.schedule.id })
        assertEquals("Push", state.schedules[0].schedule.routineName)
        assertEquals(4, state.perWeek)
        assertEquals(NextWorkout(Instant.parse("2026-09-26T18:00:00Z"), routineName = null), state.next)
        assertTrue(state.remindsAnything)
    }

    @Test
    fun `switching reminders off says nothing is next, and takes the alarm down`() = runTest {
        val viewModel = reminderList()
        viewModel.onAction(ReminderListAction.SetRemindersEnabled(true))
        assertEquals(Instant.parse("2026-09-26T17:50:00Z"), alarm.at)

        viewModel.onAction(ReminderListAction.SetRemindersEnabled(false))

        val state = viewModel.uiState.value
        assertFalse(state.remindersEnabled)
        assertNull(state.next)
        assertFalse(state.remindsAnything)
        assertNull(alarm.at)
    }

    @Test
    fun `switching one entry off moves the alarm to the next that's on`() = runTest {
        val viewModel = reminderList()

        viewModel.onAction(ReminderListAction.SetScheduleEnabled("saturday", enabled = false))

        assertFalse(viewModel.uiState.value.schedules.first { it.schedule.id == "saturday" }.schedule.isEnabled)
        assertEquals(3, viewModel.uiState.value.perWeek)
        assertEquals(Instant.parse("2026-09-28T06:50:00Z"), alarm.at)
    }

    @Test
    fun `the default lead time and the snooze apply as they're picked`() = runTest {
        val viewModel = reminderList()

        viewModel.onAction(ReminderListAction.SetDefaultLead(30))
        viewModel.onAction(ReminderListAction.SetSnooze(15))

        assertEquals(30, viewModel.uiState.value.defaultLeadMinutes)
        assertEquals(15, viewModel.uiState.value.snoozeMinutes)
        assertEquals(Instant.parse("2026-09-26T17:30:00Z"), alarm.at)
    }

    @Test
    fun `an entry skipped today says so, but only on its own day`() = runTest {
        schedules.skip("saturday", LocalDate.of(2026, 9, 26))
        schedules.skip("weekdays", LocalDate.of(2026, 9, 26))

        val items = reminderList().uiState.value.schedules

        assertTrue(items.first { it.schedule.id == "saturday" }.isSkippedToday)
        assertFalse(items.first { it.schedule.id == "weekdays" }.isSkippedToday)
    }

    @Test
    fun `a change that can't be saved says so`() = runTest {
        val viewModel = reminderList()
        settings.failure = IllegalStateException("Disk full")

        viewModel.onAction(ReminderListAction.SetDefaultLead(30))

        assertEquals(UserPreferences.DEFAULT_REMINDER_LEAD_MINUTES, viewModel.uiState.value.defaultLeadMinutes)
        assertEquals(listOf<ReminderListEvent>(ReminderListEvent.SaveFailed), events)
    }
}
