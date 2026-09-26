package com.example.liftbook.ui.feature.reminders

import androidx.lifecycle.SavedStateHandle
import com.example.liftbook.domain.model.ReminderLaunch
import com.example.liftbook.domain.model.ReminderNotice
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeReminderNotifier
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.MutableClock
import com.example.liftbook.testing.exercise
import com.example.liftbook.testing.routine
import com.example.liftbook.testing.routineExercise
import com.example.liftbook.testing.testReminders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** A reminder answered from its notification (FR-7.3, FR-7.4). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledWorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-09-26T17:50:00Z"))
    private val bench = exercise("Bench Press")
    private val exercises = FakeExerciseRepository(listOf(bench))
    private val routines = FakeRoutineRepository(exercises, listOf(routine("Push", routineExercise(bench))))
    private val workouts = FakeWorkoutRepository(routines, exercises)
    private val notifier = FakeReminderNotifier()
    private val savedState = SavedStateHandle()
    private val events = mutableListOf<ScheduledWorkoutEvent>()
    private val startsAt = Instant.parse("2026-09-26T18:00:00Z")

    private fun TestScope.scheduled(): ScheduledWorkoutViewModel =
        ScheduledWorkoutViewModel(savedState, routines, workouts, testReminders(routines = routines, notifier = notifier, clock = clock), clock)
            .also { viewModel ->
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.prompt.collect {} }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
            }

    private fun launch(routineId: String? = "push", startNow: Boolean = false) =
        ReminderLaunch(scheduleId = "sat", routineId = routineId, startsAt = startsAt, startNow = startNow)

    private fun showingReminder() = notifier.show(
        ReminderNotice("sat", startsAt, minutesToStart = 10, routineId = "push", routineName = "Push", exerciseNames = emptyList(), snoozeMinutes = 10),
    )

    @Test
    fun `tapping a reminder offers its routine, and takes the reminder down`() = runTest {
        showingReminder()
        val viewModel = scheduled()

        viewModel.onLaunch(launch(), emptyWorkoutName = "Evening workout")

        val prompt = viewModel.prompt.value!!
        assertEquals("Push", prompt.routineName)
        assertEquals(listOf("Bench Press"), prompt.exerciseNames)
        assertEquals(startsAt, prompt.startsAt)
        assertTrue(notifier.showing.isEmpty())
        assertTrue(workouts.active.value == null)
    }

    @Test
    fun `start from the offer starts the routine and opens it`() = runTest {
        val viewModel = scheduled()
        viewModel.onLaunch(launch(), emptyWorkoutName = "Evening workout")

        viewModel.onAction(ScheduledWorkoutAction.Start)

        assertEquals("push", workouts.active.value?.routineId)
        assertNull(viewModel.prompt.value)
        assertEquals(listOf<ScheduledWorkoutEvent>(ScheduledWorkoutEvent.OpenWorkout), events)
    }

    @Test
    fun `start now starts straight away, with nothing to tap`() = runTest {
        val viewModel = scheduled()

        viewModel.onLaunch(launch(startNow = true), emptyWorkoutName = "Evening workout")

        assertEquals("push", workouts.active.value?.routineId)
        assertNull(viewModel.prompt.value)
        assertEquals(listOf<ScheduledWorkoutEvent>(ScheduledWorkoutEvent.OpenWorkout), events)
    }

    @Test
    fun `with no routine, start now starts an empty workout named for the time`() = runTest {
        val viewModel = scheduled()

        viewModel.onLaunch(launch(routineId = null, startNow = true), emptyWorkoutName = "Evening workout")

        assertEquals("Evening workout", workouts.active.value?.name)
    }

    @Test
    fun `with another workout in progress, nothing starts and that one is offered`() = runTest {
        workouts.startEmpty("Morning workout")
        val viewModel = scheduled()

        viewModel.onLaunch(launch(startNow = true), emptyWorkoutName = "Evening workout")

        assertEquals("Morning workout", workouts.active.value?.name)
        assertEquals(listOf<ScheduledWorkoutEvent>(ScheduledWorkoutEvent.OtherWorkoutActive("Morning workout")), events)
    }

    @Test
    fun `a start that fails offers it again, saying so`() = runTest {
        val viewModel = scheduled()
        workouts.failNextStart = true

        viewModel.onLaunch(launch(routineId = null, startNow = true), emptyWorkoutName = "Evening workout")

        assertTrue(viewModel.prompt.value!!.startFailed)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a routine deleted since the reminder is offered as a plain workout`() = runTest {
        routines.deleteRoutine("push")
        val viewModel = scheduled()

        viewModel.onLaunch(launch(startNow = true), emptyWorkoutName = "Evening workout")

        val prompt = viewModel.prompt.value!!
        assertNull(prompt.routineName)
        viewModel.onAction(ScheduledWorkoutAction.Start)
        assertEquals("Evening workout", workouts.active.value?.name)
    }

    @Test
    fun `not now puts the offer away, and it stays away after a restart`() = runTest {
        val viewModel = scheduled()
        viewModel.onLaunch(launch(), emptyWorkoutName = "Evening workout")
        assertEquals("Push", scheduled().prompt.value?.routineName)

        viewModel.onAction(ScheduledWorkoutAction.Dismiss)

        assertNull(viewModel.prompt.value)
        assertNull(scheduled().prompt.value)
    }
}
