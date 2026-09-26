package com.example.liftbook.ui.feature.summary

import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val squat = exercise("Squat (Barbell)")
    private val exercises = FakeExerciseRepository(listOf(bench, squat))
    private val routines = FakeRoutineRepository(
        exercises,
        listOf(routine("Push", routineExercise(bench, SetTarget(sets = 2, reps = 5, weightKg = 100.0)), routineExercise(squat, SetTarget(sets = 1)), id = "push")),
    )
    private val workouts = FakeWorkoutRepository(routines, exercises)
    private val clock = Clock.fixed(Instant.parse("2026-09-25T20:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.summary(id: String = "workout-1"): WorkoutSummaryViewModel =
        WorkoutSummaryViewModel(id, workouts, FakeSettingsRepository(), clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    /** Push: one bench set of 100 × 5 done, the rest left undone, finished after an hour. */
    private suspend fun finishPush() {
        workouts.startFromRoutine("push")
        workouts.completeSet("workout-1-e0-s0", SetValues(100.0, 5), Instant.EPOCH, rest = null)
        workouts.finishWorkout("workout-1", Instant.EPOCH.plusSeconds(3_600))
    }

    @Test
    fun `sums up the finished workout`() = runTest {
        finishPush()

        val state = summary().uiState.value

        assertFalse(state.isLoading)
        assertEquals("Push", state.workout?.name)
        assertEquals(3_600L, state.summary?.durationSeconds)
        assertEquals(500.0, state.summary!!.volumeKg, 0.0)
        assertEquals(1, state.summary?.completedSets)
        // Only what was done is listed.
        assertEquals(listOf(bench), state.exercises.map { it.exercise })
        assertEquals(listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(100.0, 5))), state.exercises.single().sets)
    }

    @Test
    fun `lists the records set against earlier workouts`() = runTest {
        workouts.history[bench.id] = listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(95.0, 5)))
        finishPush()

        val records = summary().uiState.value.summary!!.records

        assertEquals(listOf(bench), records.map { it.exercise })
        assertEquals(PersonalRecord.HeaviestWeight(100.0, 5), records.single().records.first())
    }

    @Test
    fun `a workout still in progress, or gone, has no summary`() = runTest {
        workouts.startFromRoutine("push")

        assertNull(summary().uiState.value.workout)
        assertNull(summary("gone").uiState.value.workout)
        assertFalse(summary("gone").uiState.value.isLoading)
    }
}
