package com.example.liftbook.ui.feature.routines

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.domain.calculator.RoutineNameError
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeSettingsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val pullUp = exercise("Pull-Up", MuscleGroup.BACK, Equipment.NONE, ExerciseType.BODYWEIGHT)
    private val rowing = exercise("Rowing (Machine)", MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE, ExerciseType.CARDIO)
    private val exercises = FakeExerciseRepository(listOf(bench, pullUp, rowing))
    private val push = routine(
        "Push",
        routineExercise(bench, SetTarget(sets = 4, reps = 6, weightKg = 80.0)),
        routineExercise(pullUp, SetTarget(sets = 3, reps = 8)),
        id = "push",
    )
    private val routines = FakeRoutineRepository(exercises, listOf(push))
    private val settings = FakeSettingsRepository()

    private fun TestScope.editor(routineId: String? = null): RoutineEditorViewModel =
        RoutineEditorViewModel(routineId, routines, exercises, settings).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun RoutineEditorViewModel.pick(vararg exerciseIds: String) {
        onAction(RoutineEditorAction.OpenPicker)
        exerciseIds.forEach { onAction(RoutineEditorAction.TogglePicked(it)) }
        onAction(RoutineEditorAction.AddPicked)
    }

    @Test
    fun `a new form shows no errors before a save attempt`() = runTest {
        val state = editor().uiState.value

        assertFalse(state.isEditing)
        assertNull(state.nameError)
        assertFalse(state.showNoExercisesError)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `saving an empty form says what's missing and creates nothing`() = runTest {
        val viewModel = editor()

        viewModel.onAction(RoutineEditorAction.Save)

        val state = viewModel.uiState.value
        assertEquals(RoutineNameError.BLANK, state.nameError)
        assertTrue(state.showNoExercisesError)
        assertFalse(state.isSaving)
        assertEquals(1, routines.all.size)
    }

    @Test
    fun `a clashing name is flagged as it's typed`() = runTest {
        val viewModel = editor()

        viewModel.nameState.type("push")

        assertEquals(RoutineNameError.DUPLICATE, viewModel.uiState.value.nameError)
    }

    @Test
    fun `the picker searches the library and adds picks in the order picked`() = runTest {
        val viewModel = editor()

        viewModel.onAction(RoutineEditorAction.OpenPicker)
        viewModel.pickerQueryState.type("row")
        assertEquals(listOf(rowing.id), viewModel.uiState.value.picker!!.results.map { it.id })
        viewModel.onAction(RoutineEditorAction.TogglePicked(rowing.id))
        viewModel.pickerQueryState.type("")
        viewModel.onAction(RoutineEditorAction.TogglePicked(bench.id))
        viewModel.onAction(RoutineEditorAction.TogglePicked(pullUp.id))
        viewModel.onAction(RoutineEditorAction.TogglePicked(pullUp.id))
        assertEquals(listOf(rowing.id, bench.id), viewModel.uiState.value.picker!!.selectedIds)
        viewModel.onAction(RoutineEditorAction.AddPicked)

        val state = viewModel.uiState.value
        assertNull(state.picker)
        assertEquals(listOf(rowing.id, bench.id), state.exercises.map { it.exercise.id })
        assertEquals(TargetTexts("1", "", "", "", ""), state.exercises[0].fields.texts())
        assertEquals(TargetTexts("3", "10", "", "", ""), state.exercises[1].fields.texts())
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun `reopening the picker starts afresh`() = runTest {
        val viewModel = editor()
        viewModel.onAction(RoutineEditorAction.OpenPicker)
        viewModel.pickerQueryState.type("bench")
        viewModel.onAction(RoutineEditorAction.TogglePicked(bench.id))
        viewModel.onAction(RoutineEditorAction.ClosePicker)

        viewModel.onAction(RoutineEditorAction.OpenPicker)

        val picker = viewModel.uiState.value.picker!!
        assertEquals("", picker.query)
        assertTrue(picker.selectedIds.isEmpty())
        assertEquals(3, picker.results.size)
    }

    @Test
    fun `a complete form creates the routine, stored in kilograms`() = runTest {
        settings.userPreferences.value = UserPreferences(weightUnit = WeightUnit.LB)
        val viewModel = editor()
        viewModel.nameState.type("  Pull   day ")
        viewModel.pick(pullUp.id, bench.id)
        val benchFields = viewModel.uiState.value.exercises[1].fields
        benchFields.reps.type("5")
        benchFields.weight.type("185")

        viewModel.events.test {
            viewModel.onAction(RoutineEditorAction.Save)

            val saved = awaitItem() as RoutineEditorEvent.Saved
            val created = routines.all.getValue(saved.routineId)
            assertEquals("Pull day", created.name)
            assertEquals(listOf(pullUp.id, bench.id), created.exercises.map { it.exercise.id })
            assertEquals(SetTarget(sets = 3, reps = 10), created.exercises[0].target)
            assertEquals(SetTarget(sets = 3, reps = 5, weightKg = lbToKg(185.0)), created.exercises[1].target)
        }
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `an invalid set count is shown and blocks saving`() = runTest {
        val viewModel = editor()
        viewModel.nameState.type("Legs")
        viewModel.pick(bench.id)
        val fields = viewModel.uiState.value.exercises.single().fields
        fields.sets.type("0")

        viewModel.onAction(RoutineEditorAction.Save)

        assertTrue(viewModel.uiState.value.exercises.single().showSetsError)
        assertEquals(1, routines.all.size)

        // The error clears as soon as it's fixed.
        fields.sets.type("4")
        assertFalse(viewModel.uiState.value.exercises.single().showSetsError)
    }

    @Test
    fun `editing loads the routine with nothing unsaved`() = runTest {
        val state = editor(routineId = push.id).uiState.value

        assertTrue(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals(listOf(bench.id, pullUp.id), state.exercises.map { it.exercise.id })
        assertEquals(TargetTexts("4", "6", "80", "", ""), state.exercises[0].fields.texts())
        assertNull(state.nameError)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `reordering, removing and saving update the routine in place`() = runTest {
        val viewModel = editor(routineId = push.id)

        viewModel.onAction(RoutineEditorAction.MoveExercise(from = 1, to = 0))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.pick(rowing.id)
        viewModel.onAction(RoutineEditorAction.RemoveExercise(viewModel.uiState.value.exercises[1].key))

        viewModel.events.test {
            viewModel.onAction(RoutineEditorAction.Save)
            assertEquals(RoutineEditorEvent.Saved(push.id), awaitItem())
        }
        val saved = routines.all.getValue(push.id)
        assertEquals(listOf(pullUp.id, rowing.id), saved.exercises.map { it.exercise.id })
        // The kept exercise keeps its row.
        assertEquals(push.exercises[1].id, saved.exercises[0].id)
    }

    @Test
    fun `moving back to where it was is no change`() = runTest {
        val viewModel = editor(routineId = push.id)

        viewModel.onAction(RoutineEditorAction.MoveExercise(from = 0, to = 1))
        viewModel.onAction(RoutineEditorAction.MoveExercise(from = 1, to = 0))

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `moves outside the list are ignored`() = runTest {
        val viewModel = editor(routineId = push.id)

        viewModel.onAction(RoutineEditorAction.MoveExercise(from = 0, to = 5))

        assertEquals(listOf(bench.id, pullUp.id), viewModel.uiState.value.exercises.map { it.exercise.id })
    }

    @Test
    fun `a routine that no longer exists can't be edited`() = runTest {
        val state = editor(routineId = "gone").uiState.value

        assertTrue(state.isUnavailable)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a failed save says so and keeps the edit`() = runTest {
        val viewModel = editor(routineId = push.id)
        viewModel.nameState.type("Push A")
        routines.failNextWrite = true

        viewModel.events.test {
            viewModel.onAction(RoutineEditorAction.Save)
            assertEquals(RoutineEditorEvent.SaveFailed, awaitItem())
        }
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("Push", routines.all.getValue(push.id).name)
    }
}
