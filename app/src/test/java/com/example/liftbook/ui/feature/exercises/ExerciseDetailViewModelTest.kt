package com.example.liftbook.ui.feature.exercises

import app.cash.turbine.test
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.exercise
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
class ExerciseDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val benchPress = exercise("Bench Press (Barbell)")
    private val repository = FakeExerciseRepository(listOf(benchPress))
    private val settings = FakeSettingsRepository(UserPreferences(weightUnit = WeightUnit.LB))
    private val clock = Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC)

    private val lastSession = ExerciseSession(
        workoutExerciseId = "we-2",
        workoutId = "w-2",
        workoutName = "Push",
        startedAt = Instant.parse("2026-09-22T18:00:00Z"),
        sets = listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(weightKg = 80.0, reps = 5))),
    )
    private val olderSession = lastSession.copy(
        workoutExerciseId = "we-1",
        workoutId = "w-1",
        startedAt = Instant.parse("2026-09-18T18:00:00Z"),
    )

    private fun TestScope.detail(exerciseId: String = benchPress.id): ExerciseDetailViewModel =
        ExerciseDetailViewModel(exerciseId, repository, settings, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    @Test
    fun `shows the exercise with its last-performed values in the user's unit`() = runTest {
        repository.setSessions(benchPress.id, listOf(lastSession, olderSession))

        val state = detail().uiState.value

        assertFalse(state.isLoading)
        assertEquals(benchPress, state.exercise)
        assertEquals(lastSession, state.lastSession)
        assertEquals(WeightUnit.LB, state.weightUnit)
        assertEquals(LocalDate.of(2026, 9, 25), state.today)
        assertEquals(ZoneOffset.UTC, state.zone)
    }

    @Test
    fun `an exercise never performed has no last session`() = runTest {
        val state = detail().uiState.value

        assertEquals(benchPress, state.exercise)
        assertNull(state.lastSession)
    }

    @Test
    fun `a unit change applies straight away`() = runTest {
        val viewModel = detail()

        settings.userPreferences.value = UserPreferences(weightUnit = WeightUnit.KG)

        assertEquals(WeightUnit.KG, viewModel.uiState.value.weightUnit)
    }

    @Test
    fun `an unknown id loads as not found`() = runTest {
        val state = detail(exerciseId = "no-such-exercise").uiState.value

        assertFalse(state.isLoading)
        assertNull(state.exercise)
    }

    @Test
    fun `archiving archives the exercise and hands back to the caller`() = runTest {
        val viewModel = detail()

        viewModel.events.test {
            viewModel.onAction(ExerciseDetailAction.Archive)
            assertEquals(ExerciseDetailEvent.Archived(benchPress.id), awaitItem())
        }
        assertTrue(repository.all.getValue(benchPress.id).isArchived)
    }

    @Test
    fun `restoring an archived exercise updates the page`() = runTest {
        repository.archive(benchPress.id)
        val viewModel = detail()
        assertTrue(viewModel.uiState.value.exercise!!.isArchived)

        viewModel.onAction(ExerciseDetailAction.Restore)

        assertFalse(viewModel.uiState.value.exercise!!.isArchived)
    }
}
