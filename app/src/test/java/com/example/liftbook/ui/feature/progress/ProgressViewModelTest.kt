package com.example.liftbook.ui.feature.progress

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.testing.FakeBodyWeightRepository
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
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val row = exercise("Seated Row (Cable)", muscle = MuscleGroup.BACK)
    private val exercises = FakeExerciseRepository(listOf(bench, row))
    private val workouts = FakeWorkoutRepository(FakeRoutineRepository(exercises), exercises)
    private val bodyWeight = FakeBodyWeightRepository()

    /** Saturday 26 September; weeks start on Monday the 21st. */
    private val clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)

    private val settings = FakeSettingsRepository()

    private fun TestScope.progress(): ProgressViewModel =
        ProgressViewModel(workouts, FakeProgressRepository(workouts), bodyWeight, settings, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun log(id: String, startedAt: String, vararg exercises: WorkoutExercise) =
        workouts.addFinished(finishedWorkout(id, "Workout", startedAt, exercises.toList()))

    @Test
    fun `this week and last are summed from finished workouts`() = runTest {
        log(
            "mon",
            "2026-09-21T18:00:00Z",
            doneExercise("mon-b", bench, doneSet("m1", 40.0, 10, type = SetType.WARMUP), doneSet("m2", 100.0, 5), doneSet("m3", 100.0, 5)),
            doneExercise("mon-r", row, doneSet("m4", 60.0, 10)),
        )
        log("last", "2026-09-16T18:00:00Z", doneExercise("last-b", bench, doneSet("l1", 95.0, 5)))
        log("older", "2026-09-01T18:00:00Z", doneExercise("old-b", bench, doneSet("o1", 90.0, 5)))

        val week = progress().uiState.value.week!!

        assertEquals(LocalDate.of(2026, 9, 21), week.current.start)
        assertEquals(1, week.current.workouts)
        assertEquals(100.0 * 5 * 2 + 60.0 * 10, week.current.volumeKg, 1e-9)
        // Warm-ups aren't counted (FR-3.10).
        assertEquals(mapOf(MuscleGroup.CHEST to 2, MuscleGroup.BACK to 1), week.current.setsByMuscle)
        assertEquals(1, week.previous.workouts)
        assertEquals(mapOf(MuscleGroup.CHEST to 1), week.previous.setsByMuscle)
    }

    @Test
    fun `weeks start where the setting says, and move when it changes`() = runTest {
        log("sun", "2026-09-20T10:00:00Z", doneExercise("sun-b", bench, doneSet("s1", 100.0, 5)))
        val viewModel = progress()

        // Monday-start: Sunday the 20th closed last week.
        assertEquals(LocalDate.of(2026, 9, 21), viewModel.uiState.value.week!!.current.start)
        assertEquals(0, viewModel.uiState.value.week!!.current.workouts)

        settings.setFirstDayOfWeek(FirstDayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 9, 20), viewModel.uiState.value.week!!.current.start)
        assertEquals(1, viewModel.uiState.value.week!!.current.workouts)
    }

    @Test
    fun `exercises done are listed most recent first, and with none there's nothing to chart`() = runTest {
        assertTrue(progress().uiState.value.hasNoWorkouts)

        log("a", "2026-09-10T18:00:00Z", doneExercise("a-r", row, doneSet("a1", 60.0, 10)))
        log("b", "2026-09-20T18:00:00Z", doneExercise("b-b", bench, doneSet("b1", 100.0, 5)))

        val state = progress().uiState.value
        assertFalse(state.hasNoWorkouts)
        assertEquals(listOf(bench, row), state.exercises.map { it.exercise })
    }

    @Test
    fun `body weight shows the latest weigh-in and the recent trend`() = runTest {
        bodyWeight.entries.value = listOf(
            BodyWeightEntry("old", LocalDate.of(2026, 1, 1), 90.0),
            BodyWeightEntry("a", LocalDate.of(2026, 9, 1), 83.0),
            BodyWeightEntry("b", LocalDate.of(2026, 9, 25), 82.0),
        )

        val glance = progress().uiState.value.bodyWeight!!

        assertEquals("b", glance.latest.id)
        // January is older than the glance looks back.
        assertEquals(listOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 25)), glance.trend.map { it.date })
        assertEquals(-1.0, glance.change!!, 1e-9)
    }

    @Test
    fun `before the first weigh-in there's no glance, and one can be logged from here`() = runTest {
        val viewModel = progress()
        assertNull(viewModel.uiState.value.bodyWeight)

        viewModel.onAction(ProgressAction.LogBodyWeight)
        assertEquals(LocalDate.of(2026, 9, 26), viewModel.uiState.value.log!!.date)
        viewModel.logWeight.setTextAndPlaceCursorAtEnd("82,5")
        viewModel.onAction(ProgressAction.SaveLog)

        assertNull(viewModel.uiState.value.log)
        assertEquals(82.5, viewModel.uiState.value.bodyWeight!!.latest.weightKg, 1e-9)
    }
}
