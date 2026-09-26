package com.example.liftbook.ui.feature.history

import app.cash.turbine.test
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.doneExercise
import com.example.liftbook.testing.doneSet
import com.example.liftbook.testing.exercise
import com.example.liftbook.testing.finishedWorkout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val exercises = FakeExerciseRepository(listOf(bench))
    private val workouts = FakeWorkoutRepository(FakeRoutineRepository(exercises), exercises)
    private val clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)

    private val push = finishedWorkout(
        "w",
        "Push",
        "2026-09-24T18:00:00Z",
        listOf(
            doneExercise(
                "w-bench",
                bench,
                doneSet("s0", weightKg = 40.0, reps = 10, type = SetType.WARMUP),
                doneSet("s1", weightKg = 100.0, reps = 5),
            ),
        ),
        minutes = 50,
    )

    private fun TestScope.detail(id: String = "w"): WorkoutDetailViewModel =
        WorkoutDetailViewModel(id, workouts, FakeSettingsRepository(), clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    @Test
    fun `shows every set, the totals, and the records it set at the time`() = runTest {
        workouts.history[bench.id] = listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(95.0, 5)))
        workouts.addFinished(push)

        val state = detail().uiState.value

        assertFalse(state.isLoading)
        assertEquals("Push", state.workout?.name)
        assertEquals(50 * 60L, state.summary?.durationSeconds)
        // The warm-up isn't volume (FR-3.10), but it is a set done.
        assertEquals(500.0, state.summary!!.volumeKg, 0.0)
        assertEquals(2, state.summary?.completedSets)
        assertEquals(PersonalRecord.HeaviestWeight(100.0, 5), state.summary!!.records.single().records.first())
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL), state.exercises.single().sets.map { it.setType })
    }

    @Test
    fun `a workout still in progress, or gone, isn't shown`() = runTest {
        workouts.startEmpty("Now")

        assertNull(detail("workout-1").uiState.value.workout)
        assertNull(detail("gone").uiState.value.workout)
        assertFalse(detail("gone").uiState.value.isLoading)
    }

    @Test
    fun `an edit shows as soon as it's saved`() = runTest {
        workouts.addFinished(push)
        val viewModel = detail()

        workouts.addFinished(push.copy(name = "Push day"))

        assertEquals("Push day", viewModel.uiState.value.workout?.name)
    }

    @Test
    fun `deleting removes it from history and stays on screen until the screen closes`() = runTest {
        workouts.addFinished(push)
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(WorkoutDetailAction.Delete)
            assertEquals(WorkoutDetailEvent.Deleted, awaitItem())
        }

        assertTrue(workouts.finished.value.isEmpty())
        assertNotNull(viewModel.uiState.value.workout)
        assertTrue(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun `a failed delete says so and keeps the workout`() = runTest {
        workouts.addFinished(push)
        workouts.failNextWrite = true
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(WorkoutDetailAction.Delete)
            assertEquals(WorkoutDetailEvent.DeleteFailed, awaitItem())
        }

        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(setOf("w"), workouts.finished.value.keys)
    }
}
