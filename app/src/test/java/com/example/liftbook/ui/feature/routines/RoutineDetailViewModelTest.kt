package com.example.liftbook.ui.feature.routines

import app.cash.turbine.test
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.exercise
import com.example.liftbook.testing.routine
import com.example.liftbook.testing.routineExercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val exercises = FakeExerciseRepository(listOf(bench))
    private val push = routine("Push", routineExercise(bench), id = "push")
    private val pull = routine("Pull", routineExercise(bench), id = "pull")
    private val routines = FakeRoutineRepository(exercises, listOf(push, pull))
    private val workouts = FakeWorkoutRepository(routines)
    private val settings = FakeSettingsRepository(UserPreferences(weightUnit = WeightUnit.LB))
    private val clock = Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.detail(routineId: String = push.id): RoutineDetailViewModel =
        RoutineDetailViewModel(routineId, routines, workouts, settings, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    @Test
    fun `shows the routine in the user's unit`() = runTest {
        val state = detail().uiState.value

        assertFalse(state.isLoading)
        assertEquals(push, state.routine)
        assertEquals(WeightUnit.LB, state.weightUnit)
        assertEquals(LocalDate.of(2026, 9, 25), state.today)
    }

    @Test
    fun `a routine that doesn't exist loads as not found`() = runTest {
        val state = detail(routineId = "gone").uiState.value

        assertFalse(state.isLoading)
        assertNull(state.routine)
    }

    @Test
    fun `starting opens the new workout`() = runTest {
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(RoutineDetailAction.Start)
            assertEquals(RoutineDetailEvent.OpenWorkout, awaitItem())
        }
        assertEquals(listOf(push.id), workouts.started)
        assertFalse(viewModel.uiState.value.isStarting)
    }

    @Test
    fun `starting the routine already in progress goes back to it`() = runTest {
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(RoutineDetailAction.Start)
            awaitItem()
            viewModel.onAction(RoutineDetailAction.Start)
            assertEquals(RoutineDetailEvent.OpenWorkout, awaitItem())
        }
        assertEquals(listOf(push.id), workouts.started)
    }

    @Test
    fun `another workout in progress is named, and nothing starts`() = runTest {
        workouts.startFromRoutine(pull.id)
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(RoutineDetailAction.Start)
            assertEquals(RoutineDetailEvent.OtherWorkoutActive("Pull"), awaitItem())
        }
        assertEquals(listOf(pull.id), workouts.started)
    }

    @Test
    fun `a failed start says so`() = runTest {
        workouts.failNextStart = true
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(RoutineDetailAction.Start)
            assertEquals(RoutineDetailEvent.StartFailed, awaitItem())
        }
        assertFalse(viewModel.uiState.value.isStarting)
    }

    @Test
    fun `duplicating names the copy`() = runTest {
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(RoutineDetailAction.Duplicate)
            val event = awaitItem() as RoutineDetailEvent.Duplicated
            assertEquals("Push 2", event.name)
            assertEquals("Push 2", routines.all.getValue(event.routineId).name)
        }
    }

    @Test
    fun `deleting removes the routine`() = runTest {
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(RoutineDetailAction.Delete)
            assertEquals(RoutineDetailEvent.Deleted, awaitItem())
        }
        assertNull(routines.all[push.id])
    }
}
