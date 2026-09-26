package com.example.liftbook.ui.feature.exercises

import com.example.liftbook.domain.calculator.ExerciseNameError
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup

data class ExerciseEditorUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    /** The exercise doesn't exist or is built-in, so there's nothing the user may edit. */
    val isUnavailable: Boolean = false,
    val primaryMuscle: MuscleGroup? = null,
    val equipment: Equipment? = null,
    val type: ExerciseType = ExerciseType.STRENGTH,
    /** Sets are logged against the exercise, so changing its type would change what they mean. */
    val isTypeLocked: Boolean = false,
    /** A problem with the name. A duplicate shows as the user types; a blank name only after a save attempt. */
    val nameError: ExerciseNameError? = null,
    val showMuscleError: Boolean = false,
    val showEquipmentError: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface ExerciseEditorAction {
    data class SelectMuscle(val muscleGroup: MuscleGroup) : ExerciseEditorAction

    data class SelectEquipment(val equipment: Equipment) : ExerciseEditorAction

    data class SelectType(val type: ExerciseType) : ExerciseEditorAction

    data object Save : ExerciseEditorAction

    /** Leave without saving. The screen confirms first when there are unsaved changes. */
    data object Close : ExerciseEditorAction
}

sealed interface ExerciseEditorEvent {
    data class Saved(val exerciseId: String) : ExerciseEditorEvent

    data object SaveFailed : ExerciseEditorEvent
}
