package com.example.liftbook.ui.feature.exercises

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import com.example.liftbook.domain.calculator.ExerciseNameError
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
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
class ExerciseEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val benchPress = exercise("Bench Press (Barbell)")
    private val yRaise = exercise(
        "Cable Y-Raise", MuscleGroup.SHOULDERS, Equipment.CABLE, id = "y-raise", isCustom = true,
    )
    private val repository = FakeExerciseRepository(listOf(benchPress, yRaise))

    private fun TestScope.editor(exerciseId: String? = null, initialName: String? = null): ExerciseEditorViewModel =
        ExerciseEditorViewModel(exerciseId, initialName, repository).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun ExerciseEditorViewModel.typeName(name: String) {
        nameState.setTextAndPlaceCursorAtEnd(name)
        Snapshot.sendApplyNotifications()
    }

    private fun ExerciseEditorViewModel.fillIn(name: String) {
        typeName(name)
        onAction(ExerciseEditorAction.SelectMuscle(MuscleGroup.QUADS))
        onAction(ExerciseEditorAction.SelectEquipment(Equipment.BARBELL))
    }

    @Test
    fun `a new form doesn't show errors before a save attempt`() = runTest {
        val state = editor().uiState.value

        assertFalse(state.isEditing)
        assertNull(state.nameError)
        assertFalse(state.showMuscleError)
        assertFalse(state.showEquipmentError)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `saving an incomplete form shows what's missing and creates nothing`() = runTest {
        val viewModel = editor()

        viewModel.onAction(ExerciseEditorAction.Save)

        val state = viewModel.uiState.value
        assertEquals(ExerciseNameError.BLANK, state.nameError)
        assertTrue(state.showMuscleError)
        assertTrue(state.showEquipmentError)
        assertFalse(state.isSaving)
        assertEquals(2, repository.all.size)
    }

    @Test
    fun `a duplicate name is flagged as it's typed`() = runTest {
        val viewModel = editor()

        viewModel.typeName("bench press (barbell)")

        assertEquals(ExerciseNameError.DUPLICATE, viewModel.uiState.value.nameError)
    }

    @Test
    fun `a complete form creates a custom exercise with a tidied name`() = runTest {
        val viewModel = editor()

        viewModel.events.test {
            viewModel.fillIn("  Zercher   Squat ")
            viewModel.onAction(ExerciseEditorAction.Save)

            val saved = awaitItem() as ExerciseEditorEvent.Saved
            val created = repository.all.getValue(saved.exerciseId)
            assertEquals("Zercher Squat", created.name)
            assertEquals(MuscleGroup.QUADS, created.primaryMuscle)
            assertEquals(Equipment.BARBELL, created.equipment)
            assertEquals(ExerciseType.STRENGTH, created.type)
            assertTrue(created.isCustom)
        }
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `the type follows muscle and equipment until the user picks one`() = runTest {
        val viewModel = editor()

        viewModel.onAction(ExerciseEditorAction.SelectEquipment(Equipment.NONE))
        assertEquals(ExerciseType.BODYWEIGHT, viewModel.uiState.value.type)

        viewModel.onAction(ExerciseEditorAction.SelectMuscle(MuscleGroup.CARDIO))
        assertEquals(ExerciseType.CARDIO, viewModel.uiState.value.type)

        viewModel.onAction(ExerciseEditorAction.SelectType(ExerciseType.STRENGTH))
        viewModel.onAction(ExerciseEditorAction.SelectEquipment(Equipment.BAND))
        assertEquals(ExerciseType.STRENGTH, viewModel.uiState.value.type)
    }

    @Test
    fun `a name carried over from search is prefilled and isn't an unsaved change`() = runTest {
        val viewModel = editor(initialName = "Zercher Squat")

        assertEquals("Zercher Squat", viewModel.nameState.text.toString())
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.onAction(ExerciseEditorAction.SelectMuscle(MuscleGroup.QUADS))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `editing loads the exercise with nothing unsaved`() = runTest {
        val viewModel = editor(exerciseId = yRaise.id)

        val state = viewModel.uiState.value
        assertTrue(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals("Cable Y-Raise", viewModel.nameState.text.toString())
        assertEquals(MuscleGroup.SHOULDERS, state.primaryMuscle)
        assertEquals(Equipment.CABLE, state.equipment)
        // Its own name isn't a clash.
        assertNull(state.nameError)
        assertFalse(state.hasUnsavedChanges)
        assertFalse(state.isTypeLocked)
    }

    @Test
    fun `editing saves changes to the same exercise`() = runTest {
        val viewModel = editor(exerciseId = yRaise.id)

        viewModel.events.test {
            viewModel.typeName("Cable Y Raise")
            viewModel.onAction(ExerciseEditorAction.Save)
            assertEquals(ExerciseEditorEvent.Saved(yRaise.id), awaitItem())
        }
        assertEquals("Cable Y Raise", repository.all.getValue(yRaise.id).name)
        assertEquals(2, repository.all.size)
    }

    @Test
    fun `the type is locked once sets are logged against the exercise`() = runTest {
        repository.exercisesWithSets += yRaise.id
        val viewModel = editor(exerciseId = yRaise.id)

        assertTrue(viewModel.uiState.value.isTypeLocked)
        viewModel.onAction(ExerciseEditorAction.SelectType(ExerciseType.CARDIO))
        assertEquals(ExerciseType.STRENGTH, viewModel.uiState.value.type)
    }

    @Test
    fun `built-in and missing exercises can't be edited`() = runTest {
        assertTrue(editor(exerciseId = benchPress.id).uiState.value.isUnavailable)
        assertTrue(editor(exerciseId = "no-such-exercise").uiState.value.isUnavailable)
    }

    @Test
    fun `a failed save is reported and can be retried`() = runTest {
        val viewModel = editor()
        viewModel.fillIn("Zercher Squat")
        repository.failNextWrite = true

        viewModel.events.test {
            viewModel.onAction(ExerciseEditorAction.Save)
            assertEquals(ExerciseEditorEvent.SaveFailed, awaitItem())
            assertFalse(viewModel.uiState.value.isSaving)

            viewModel.onAction(ExerciseEditorAction.Save)
            assertTrue(awaitItem() is ExerciseEditorEvent.Saved)
        }
    }
}
