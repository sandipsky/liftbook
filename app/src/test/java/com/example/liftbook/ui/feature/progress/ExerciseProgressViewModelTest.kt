package com.example.liftbook.ui.feature.progress

import androidx.lifecycle.SavedStateHandle
import com.example.liftbook.domain.calculator.oneRepMax
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeProgressRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val pullUp = exercise("Pull-Up", muscle = MuscleGroup.BACK, equipment = Equipment.NONE, type = ExerciseType.BODYWEIGHT)
    private val exercises = FakeExerciseRepository(listOf(bench, pullUp))
    private val workouts = FakeWorkoutRepository(FakeRoutineRepository(exercises), exercises)
    private val clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)
    private val savedState = SavedStateHandle()

    private fun TestScope.progress(exerciseId: String = bench.id): ExerciseProgressViewModel =
        ExerciseProgressViewModel(exerciseId, exercises, FakeProgressRepository(workouts), FakeSettingsRepository(), clock, savedState).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun logBench(id: String, startedAt: String, kg: Double, reps: Int) = workouts.addFinished(
        finishedWorkout(id, "Push", startedAt, listOf(doneExercise("$id-e", bench, doneSet("$id-w", 40.0, 10, type = SetType.WARMUP), doneSet("$id-s", kg, reps)))),
    )

    @Test
    fun `opens on the type's main metric over the last three months`() = runTest {
        logBench("old", "2026-04-01T18:00:00Z", 80.0, 5)
        logBench("a", "2026-08-01T18:00:00Z", 90.0, 5)
        logBench("b", "2026-09-20T18:00:00Z", 100.0, 5)

        val state = progress().uiState.value

        assertFalse(state.isLoading)
        assertEquals(ProgressMetric.forType(ExerciseType.STRENGTH), state.metrics)
        assertEquals(ProgressMetric.ESTIMATED_ONE_REP_MAX, state.metric)
        assertEquals(ProgressRange.THREE_MONTHS, state.range)
        assertEquals(listOf("a", "b"), state.points.map { it.workoutId })
        assertEquals(oneRepMax(100.0, 5) - oneRepMax(90.0, 5), state.summary!!.change, 1e-9)
        assertTrue(state.hasHistory)
    }

    @Test
    fun `changing the metric or range redraws from the same workouts, and survives the process`() = runTest {
        logBench("old", "2026-04-01T18:00:00Z", 80.0, 5)
        logBench("b", "2026-09-20T18:00:00Z", 100.0, 5)
        val viewModel = progress()

        viewModel.onAction(ExerciseProgressAction.SelectMetric(ProgressMetric.MAX_WEIGHT))
        viewModel.onAction(ExerciseProgressAction.SelectRange(ProgressRange.ALL))

        val state = viewModel.uiState.value
        assertEquals(listOf(80.0, 100.0), state.points.map { it.value })
        // A new ViewModel on the same saved state picks up where it was.
        assertEquals(ProgressMetric.MAX_WEIGHT, progress().uiState.value.metric)
    }

    @Test
    fun `nothing in range, but earlier workouts, offers all time`() = runTest {
        logBench("old", "2026-04-01T18:00:00Z", 80.0, 5)

        val state = progress().uiState.value

        assertTrue(state.points.isEmpty())
        assertNull(state.summary)
        assertTrue(state.hasHistory)
        assertTrue(state.metricHasValues)
    }

    @Test
    fun `a bodyweight exercise charts reps`() = runTest {
        workouts.addFinished(
            finishedWorkout("p", "Pull", "2026-09-20T18:00:00Z", listOf(doneExercise("p-e", pullUp, doneSet("p-1", reps = 12), doneSet("p-2", reps = 9)))),
        )

        val state = progress(pullUp.id).uiState.value

        assertEquals(ProgressMetric.MOST_REPS, state.metric)
        assertEquals(12.0, state.points.single().value, 0.0)
    }

    @Test
    fun `a metric saved for another type falls back to this one's default`() = runTest {
        savedState["metric"] = ProgressMetric.TOTAL_DISTANCE.name

        assertEquals(ProgressMetric.ESTIMATED_ONE_REP_MAX, progress().uiState.value.metric)
    }

    @Test
    fun `an exercise never done has nothing to chart, and an unknown one isn't found`() = runTest {
        val state = progress().uiState.value
        assertFalse(state.hasHistory)
        assertEquals(bench, state.exercise)

        assertNull(progress("no-such-exercise").uiState.value.exercise)
    }
}
