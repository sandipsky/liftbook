package com.example.liftbook.ui.feature.exercises

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.testing.FakeExerciseRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val benchBarbell = exercise("Bench Press (Barbell)", MuscleGroup.CHEST, Equipment.BARBELL)
    private val benchDumbbell = exercise("Bench Press (Dumbbell)", MuscleGroup.CHEST, Equipment.DUMBBELL)
    private val oneArmRow = exercise("One-Arm Row (Dumbbell)", MuscleGroup.BACK, Equipment.DUMBBELL)
    private val pullUp = exercise("Pull-Up", MuscleGroup.BACK, Equipment.NONE)

    private val repository = FakeExerciseRepository(listOf(benchBarbell, benchDumbbell, oneArmRow, pullUp))
    private val savedStateHandle = SavedStateHandle()

    private fun TestScope.library(): ExerciseLibraryViewModel =
        ExerciseLibraryViewModel(repository, savedStateHandle).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun ExerciseLibraryViewModel.type(query: String) {
        queryState.setTextAndPlaceCursorAtEnd(query)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `browsing shows the library in alphabetical sections`() = runTest {
        val state = library().uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf("B", "O", "P"), state.sections.map { it.title })
        assertEquals(listOf(benchBarbell, benchDumbbell), state.sections.first().exercises)
        assertEquals(4, state.libraryCount)
        assertFalse(state.isFiltering)
    }

    @Test
    fun `searching shows ranked matches in a single untitled section`() = runTest {
        val viewModel = library()

        viewModel.type("  bench ")

        val state = viewModel.uiState.value
        assertEquals("bench", state.query)
        assertEquals(listOf(ExerciseSection(null, listOf(benchBarbell, benchDumbbell))), state.sections)
        assertEquals(2, state.resultCount)
        assertTrue(state.isFiltering)
    }

    @Test
    fun `a search with no matches reports zero results`() = runTest {
        val viewModel = library()

        viewModel.type("zercher")

        assertEquals(0, viewModel.uiState.value.resultCount)
        assertEquals(4, viewModel.uiState.value.libraryCount)
    }

    @Test
    fun `filters narrow the results and clear together`() = runTest {
        val viewModel = library()

        viewModel.onAction(ExerciseLibraryAction.FilterByMuscle(MuscleGroup.BACK))
        assertEquals(listOf(oneArmRow, pullUp), viewModel.uiState.value.sections.single().exercises)

        viewModel.onAction(ExerciseLibraryAction.FilterByEquipment(Equipment.DUMBBELL))
        assertEquals(listOf(oneArmRow), viewModel.uiState.value.sections.single().exercises)

        viewModel.onAction(ExerciseLibraryAction.ClearFilters)
        val state = viewModel.uiState.value
        assertNull(state.muscleFilter)
        assertNull(state.equipmentFilter)
        assertEquals(4, state.resultCount)
    }

    @Test
    fun `archiving elsewhere removes the exercise from the library`() = runTest {
        val viewModel = library()

        repository.archive(pullUp.id)

        assertEquals(3, viewModel.uiState.value.libraryCount)
        assertFalse(viewModel.uiState.value.sections.flatMap { it.exercises }.any { it.id == pullUp.id })
    }

    @Test
    fun `an exercise archived from its page is offered for undo, once`() = runTest {
        val viewModel = library()
        repository.archive(pullUp.id)

        viewModel.events.test {
            savedStateHandle[ExerciseLibraryViewModel.ARCHIVED_EXERCISE_ID] = pullUp.id
            assertEquals(ExerciseLibraryEvent.ExerciseArchived(pullUp.id, pullUp.name), awaitItem())
            expectNoEvents()
        }
        assertNull(savedStateHandle[ExerciseLibraryViewModel.ARCHIVED_EXERCISE_ID])
    }

    @Test
    fun `undo restores the archived exercise`() = runTest {
        val viewModel = library()
        repository.archive(pullUp.id)

        viewModel.onAction(ExerciseLibraryAction.RestoreExercise(pullUp.id))

        assertFalse(repository.all.getValue(pullUp.id).isArchived)
        assertEquals(4, viewModel.uiState.value.libraryCount)
    }
}
