package com.example.liftbook.ui.feature.reminders

import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeReminderAlarm
import com.example.liftbook.testing.FakeReminderNotifier
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeScheduleRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.MutableClock
import com.example.liftbook.testing.routine
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Saturday 26 September 2026, mid-morning. */
    private val clock = MutableClock(Instant.parse("2026-09-26T10:00:00Z"))
    private val routines = FakeRoutineRepository(FakeExerciseRepository(), listOf(routine("Push"), routine("Legs")))
    private val schedules = FakeScheduleRepository()
    private val settings = FakeSettingsRepository()
    private val alarm = FakeReminderAlarm()
    private val notifier = FakeReminderNotifier()
    private val events = mutableListOf<ReminderEditorEvent>()

    private fun TestScope.editor(scheduleId: String? = null): ReminderEditorViewModel =
        ReminderEditorViewModel(
            scheduleId,
            schedules,
            routines,
            settings,
            testReminders(schedules = schedules, routines = routines, settings = settings, alarm = alarm, notifier = notifier, clock = clock),
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        }

    @Test
    fun `a new entry starts in the evening, on no day, following the default lead time`() = runTest {
        val state = editor().uiState.value

        assertFalse(state.isEditing)
        assertEquals(LocalTime.of(18, 0), state.startTime)
        assertTrue(state.days.isEmpty())
        assertNull(state.routine)
        assertNull(state.leadMinutes)
        assertEquals(listOf("Legs", "Push"), state.routines.map { it.name })
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `saving with no day says a day is needed, and saves nothing`() = runTest {
        val viewModel = editor()

        viewModel.onAction(ReminderEditorAction.Save)

        assertTrue(viewModel.uiState.value.showDaysError)
        assertTrue(schedules.schedules.value.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `saving schedules the workout and sets the alarm for it`() = runTest {
        val viewModel = editor()
        viewModel.onAction(ReminderEditorAction.ToggleDay(DayOfWeek.SATURDAY))
        viewModel.onAction(ReminderEditorAction.ToggleDay(DayOfWeek.MONDAY))
        viewModel.onAction(ReminderEditorAction.SetStartTime(LocalTime.of(19, 30)))
        viewModel.onAction(ReminderEditorAction.SetRoutine("push"))
        viewModel.onAction(ReminderEditorAction.SetLead(30))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onAction(ReminderEditorAction.Save)

        val saved = schedules.schedules.value.single()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), saved.days)
        assertEquals(LocalTime.of(19, 30), saved.startTime)
        assertEquals("push", saved.routineId)
        assertEquals(30, saved.leadMinutes)
        assertEquals(Instant.parse("2026-09-26T19:00:00Z"), alarm.at)
        assertEquals(listOf<ReminderEditorEvent>(ReminderEditorEvent.Saved), events)
    }

    @Test
    fun `an entry opens as saved, and picking a day twice leaves nothing to save`() = runTest {
        schedules.schedules.value = listOf(
            WorkoutSchedule(id = "s", days = setOf(DayOfWeek.FRIDAY), startTime = LocalTime.of(6, 30), routineId = "legs", leadMinutes = 15),
        )
        val viewModel = editor("s")

        val loaded = viewModel.uiState.value
        assertTrue(loaded.isEditing)
        assertEquals(setOf(DayOfWeek.FRIDAY), loaded.days)
        assertEquals(LocalTime.of(6, 30), loaded.startTime)
        assertEquals(RoutineOption("legs", "Legs"), loaded.routine)
        assertEquals(15, loaded.leadMinutes)

        viewModel.onAction(ReminderEditorAction.ToggleDay(DayOfWeek.MONDAY))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.onAction(ReminderEditorAction.ToggleDay(DayOfWeek.MONDAY))
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `saving an edit turns the entry back on`() = runTest {
        schedules.schedules.value = listOf(WorkoutSchedule(id = "s", days = setOf(DayOfWeek.FRIDAY), startTime = LocalTime.of(6, 30), isEnabled = false))
        val viewModel = editor("s")

        viewModel.onAction(ReminderEditorAction.SetStartTime(LocalTime.of(7, 0)))
        viewModel.onAction(ReminderEditorAction.Save)

        val saved = schedules.get("s")!!
        assertEquals(LocalTime.of(7, 0), saved.startTime)
        assertTrue(saved.isEnabled)
    }

    @Test
    fun `a routine deleted while editing isn't linked on save`() = runTest {
        val viewModel = editor()
        viewModel.onAction(ReminderEditorAction.ToggleDay(DayOfWeek.MONDAY))
        viewModel.onAction(ReminderEditorAction.SetRoutine("push"))
        routines.deleteRoutine("push")

        viewModel.onAction(ReminderEditorAction.Save)

        assertNull(viewModel.uiState.value.routine)
        assertNull(schedules.schedules.value.single().routineId)
    }

    @Test
    fun `deleting an entry takes its reminder and alarm with it`() = runTest {
        schedules.schedules.value = listOf(WorkoutSchedule(id = "s", days = setOf(DayOfWeek.SATURDAY), startTime = LocalTime.of(18, 0)))
        val viewModel = editor("s")

        viewModel.onAction(ReminderEditorAction.Delete)

        assertTrue(schedules.schedules.value.isEmpty())
        assertNull(alarm.at)
        assertEquals(listOf<ReminderEditorEvent>(ReminderEditorEvent.Deleted), events)
    }

    @Test
    fun `an entry that no longer exists says so`() = runTest {
        assertTrue(editor("gone").uiState.value.isUnavailable)
    }

    @Test
    fun `a save that fails says so, and keeps the edit`() = runTest {
        val viewModel = editor()
        viewModel.onAction(ReminderEditorAction.ToggleDay(DayOfWeek.MONDAY))
        schedules.failNextWrite = true

        viewModel.onAction(ReminderEditorAction.Save)

        assertEquals(listOf<ReminderEditorEvent>(ReminderEditorEvent.SaveFailed), events)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
