package com.example.liftbook.ui.feature.routines

import com.example.liftbook.domain.calculator.RoutineNameError
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.ExercisePickerUiState

data class RoutineEditorUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    /** The routine being edited no longer exists. */
    val isUnavailable: Boolean = false,
    /** In order. */
    val exercises: List<RoutineEditorExercise> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** A duplicate shows as the user types; a blank name only after a save attempt. */
    val nameError: RoutineNameError? = null,
    val showNoExercisesError: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    /** The exercise picker, while it's open. */
    val picker: ExercisePickerUiState? = null,
)

data class RoutineEditorExercise(
    /** Stable for the life of the editor, including across reorders. */
    val key: String,
    val exercise: Exercise,
    val fields: TargetFields,
    /** Shown after a save attempt when the set count is missing or out of range. */
    val showSetsError: Boolean = false,
)

sealed interface RoutineEditorAction {
    data object OpenPicker : RoutineEditorAction

    data object ClosePicker : RoutineEditorAction

    data class TogglePicked(val exerciseId: String) : RoutineEditorAction

    /** Adds the picked exercises, in the order they were picked, with default targets. */
    data object AddPicked : RoutineEditorAction

    /** Moves the exercise at [from] to [to]; both are positions in the exercise list (FR-2.2). */
    data class MoveExercise(val from: Int, val to: Int) : RoutineEditorAction

    data class RemoveExercise(val key: String) : RoutineEditorAction

    data object Save : RoutineEditorAction

    /** Leave without saving. The screen confirms first when there are unsaved changes. */
    data object Close : RoutineEditorAction
}

sealed interface RoutineEditorEvent {
    data class Saved(val routineId: String) : RoutineEditorEvent

    data object SaveFailed : RoutineEditorEvent
}
