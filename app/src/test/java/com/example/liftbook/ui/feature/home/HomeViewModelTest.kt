package com.example.liftbook.ui.feature.home

import app.cash.turbine.test
import com.example.liftbook.domain.model.RoutineDraft
import com.example.liftbook.domain.model.RoutineExerciseDraft
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRoutineRepository
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
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val exercises = FakeExerciseRepository(listOf(bench))
    private val push = routine("Push", routineExercise(bench), id = "push", lastPerformedAt = Instant.parse("2026-09-23T18:00:00Z"))
    private val legs = routine("Legs", routineExercise(bench), id = "legs")
    private val routines = FakeRoutineRepository(exercises, listOf(push, legs))
    private val workouts = FakeWorkoutRepository(routines)
    private val clock = Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.home(): HomeViewModel =
        HomeViewModel(routines, workouts, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    @Test
    fun `lists routines by name, with when each was last done`() = runTest {
        val state = home().uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("Legs", "Push"), state.routines.map { it.name })
        assertEquals(push.lastPerformedAt, state.routines[1].lastPerformedAt)
        assertEquals(LocalDate.of(2026, 9, 25), state.today)
    }

    @Test
    fun `a new routine appears without a reload`() = runTest {
        val viewModel = home()

        routines.createRoutine(RoutineDraft("Arms", listOf(RoutineExerciseDraft(bench.id, SetTarget(sets = 3)))))

        assertEquals(listOf("Arms", "Legs", "Push"), viewModel.uiState.value.routines.map { it.name })
    }

    @Test
    fun `starting a routine opens its workout`() = runTest {
        val viewModel = home()

        viewModel.events.test {
            viewModel.onAction(HomeAction.StartRoutine(push.id))
            assertEquals(HomeEvent.OpenWorkout, awaitItem())
        }
        assertEquals(listOf(push.id), workouts.started)
    }

    @Test
    fun `starting another routine while one is in progress names the one in progress`() = runTest {
        val viewModel = home()

        viewModel.events.test {
            viewModel.onAction(HomeAction.StartRoutine(push.id))
            awaitItem()
            viewModel.onAction(HomeAction.StartRoutine(legs.id))
            assertEquals(HomeEvent.OtherWorkoutActive("Push"), awaitItem())
        }
        assertEquals(listOf(push.id), workouts.started)
    }

    @Test
    fun `starting an empty workout opens it, named as asked`() = runTest {
        val viewModel = home()

        viewModel.events.test {
            viewModel.onAction(HomeAction.StartEmpty("Morning workout"))
            assertEquals(HomeEvent.OpenWorkout, awaitItem())
        }
        assertEquals("Morning workout", workouts.active.value?.name)
        assertEquals(null, workouts.active.value?.routineId)
    }

    @Test
    fun `an empty workout can't start while another is in progress`() = runTest {
        val viewModel = home()

        viewModel.events.test {
            viewModel.onAction(HomeAction.StartRoutine(push.id))
            awaitItem()
            viewModel.onAction(HomeAction.StartEmpty("Morning workout"))
            assertEquals(HomeEvent.OtherWorkoutActive("Push"), awaitItem())
        }
    }

    @Test
    fun `a routine deleted meanwhile can't be started`() = runTest {
        val viewModel = home()

        viewModel.events.test {
            viewModel.onAction(HomeAction.StartRoutine("gone"))
            assertEquals(HomeEvent.RoutineMissing, awaitItem())
        }
    }

    @Test
    fun `a failed start says so, and the next tap tries again`() = runTest {
        workouts.failNextStart = true
        val viewModel = home()

        viewModel.events.test {
            viewModel.onAction(HomeAction.StartRoutine(push.id))
            assertEquals(HomeEvent.StartFailed, awaitItem())
            viewModel.onAction(HomeAction.StartRoutine(push.id))
            assertEquals(HomeEvent.OpenWorkout, awaitItem())
        }
    }
}
