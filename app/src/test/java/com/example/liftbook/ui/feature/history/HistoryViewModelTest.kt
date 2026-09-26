package com.example.liftbook.ui.feature.history

import androidx.lifecycle.SavedStateHandle
import com.example.liftbook.domain.calculator.WorkoutTotals
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.WorkoutListItem
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val exercises = FakeExerciseRepository(listOf(bench))
    private val workouts = FakeWorkoutRepository(FakeRoutineRepository(exercises), exercises)
    private val clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)
    private val savedState = SavedStateHandle()
    private val settings = FakeSettingsRepository()

    private fun TestScope.history(): HistoryViewModel =
        HistoryViewModel(workouts, settings, clock, savedState).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun logBench(id: String, startedAt: String, minutes: Long = 60) = workouts.addFinished(
        finishedWorkout(id, "Push", startedAt, listOf(doneExercise("$id-e", bench, doneSet("$id-s", weightKg = 100.0, reps = 5))), minutes),
    )

    @Test
    fun `opens on the list, without querying a calendar`() = runTest {
        val state = history().uiState.value

        assertEquals(HistoryView.List, state.view)
        assertNull(state.calendar)
    }

    @Test
    fun `the calendar shows this month's training days and what they add up to`() = runTest {
        logBench("a", "2026-09-22T07:00:00Z", minutes = 45)
        logBench("b", "2026-09-22T18:00:00Z")
        logBench("c", "2026-09-03T18:00:00Z")
        logBench("august", "2026-08-30T18:00:00Z")
        val viewModel = history()

        viewModel.onAction(HistoryAction.ShowView(HistoryView.Calendar))

        val calendar = viewModel.uiState.value.calendar!!
        assertEquals(YearMonth.of(2026, 9), calendar.month)
        // Two on the 22nd, oldest first; August's isn't on September's calendar.
        assertEquals(listOf("a", "b"), calendar.days[LocalDate.of(2026, 9, 22)]?.map { it.id })
        assertEquals(setOf(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 22)), calendar.days.keys)
        assertEquals(WorkoutTotals(workouts = 3, durationSeconds = (45 + 60 + 60) * 60L, volumeKg = 1_500.0), calendar.totals)
        assertFalse(calendar.hasNext)
    }

    @Test
    fun `the calendar goes back a month at a time, and never past this one`() = runTest {
        logBench("august", "2026-08-30T18:00:00Z")
        val viewModel = history()
        viewModel.onAction(HistoryAction.ShowView(HistoryView.Calendar))

        viewModel.onAction(HistoryAction.NextMonth)
        assertEquals(YearMonth.of(2026, 9), viewModel.uiState.value.calendar?.month)

        viewModel.onAction(HistoryAction.PreviousMonth)
        val august = viewModel.uiState.value.calendar!!
        assertEquals(YearMonth.of(2026, 8), august.month)
        assertEquals(listOf("august"), august.days.values.flatten().map { it.id })
        assertTrue(august.hasNext)

        viewModel.onAction(HistoryAction.NextMonth)
        assertEquals(YearMonth.of(2026, 9), viewModel.uiState.value.calendar?.month)
    }

    @Test
    fun `the view and month outlive the process`() = runTest {
        history().run {
            onAction(HistoryAction.ShowView(HistoryView.Calendar))
            onAction(HistoryAction.PreviousMonth)
        }

        // A new ViewModel over the same saved state, as after the process was killed.
        val state = history().uiState.value

        assertEquals(HistoryView.Calendar, state.view)
        assertEquals(YearMonth.of(2026, 8), state.calendar?.month)
    }

    @Test
    fun `a change to history shows on the calendar at once`() = runTest {
        val viewModel = history()
        viewModel.onAction(HistoryAction.ShowView(HistoryView.Calendar))
        assertTrue(viewModel.uiState.value.calendar!!.days.isEmpty())

        logBench("new", "2026-09-25T18:00:00Z")

        assertEquals(listOf("new"), viewModel.uiState.value.calendar!!.days[LocalDate.of(2026, 9, 25)]?.map { it.id })
    }

    @Test
    fun `the calendar's weeks start on the day the setting says`() = runTest {
        val viewModel = history()
        assertEquals(DayOfWeek.MONDAY, viewModel.uiState.value.firstDayOfWeek)

        settings.setFirstDayOfWeek(FirstDayOfWeek.SUNDAY)

        assertEquals(DayOfWeek.SUNDAY, viewModel.uiState.value.firstDayOfWeek)
    }

    @Test
    fun `each month in the list starts with its heading`() {
        val zone = ZoneOffset.UTC
        val late = item("2026-09-20T18:00:00Z")
        val early = item("2026-09-02T18:00:00Z")
        val august = item("2026-08-30T18:00:00Z")

        assertEquals(YearMonth.of(2026, 9), monthHeadingBetween(null, late, zone))
        assertNull(monthHeadingBetween(late, early, zone))
        assertEquals(YearMonth.of(2026, 8), monthHeadingBetween(early, august, zone))
        assertNull(monthHeadingBetween(august, null, zone))
    }

    private fun item(startedAt: String) = Instant.parse(startedAt).let { start ->
        WorkoutListItem(startedAt, "Push", start, start.plusSeconds(3_600), 0.0, 0, emptyList())
    }
}
