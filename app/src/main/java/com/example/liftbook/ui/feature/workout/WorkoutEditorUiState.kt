package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import com.example.liftbook.domain.calculator.WorkoutSpan
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.ExercisePickerUiState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class WorkoutEditorUiState(
    val isLoading: Boolean = true,
    /** There's no finished workout with this id: it was deleted, or it's still in progress. */
    val isUnavailable: Boolean = false,
    /** When it started and ended, as edited so far. */
    val span: WorkoutSpan? = null,
    /** In order. Every set is done, so the rows have no done toggle. */
    val exercises: List<ActiveExercise> = emptyList(),
    /** The workout's note (FR-3.9); null until the workout has loaded. */
    val workoutNote: TextFieldState? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    /** The zone the times are shown and edited in. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /** A save was tried with no name. */
    val showNameError: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    /** Exercises shown as a compact list to drag into a new order. */
    val isReordering: Boolean = false,
    /** The exercise picker, while it's open. */
    val picker: ExercisePickerUiState? = null,
)

sealed interface WorkoutEditorAction {
    data object Save : WorkoutEditorAction

    /** Delete the workout instead, once the screen has confirmed it: the choice when nothing is left in it. */
    data object Delete : WorkoutEditorAction

    // When it was (FR-4.2)
    data class SetDate(val date: LocalDate) : WorkoutEditorAction

    data class SetStartTime(val time: LocalTime) : WorkoutEditorAction

    data class SetEndTime(val time: LocalTime) : WorkoutEditorAction

    // Exercises
    data object OpenPicker : WorkoutEditorAction

    data object ClosePicker : WorkoutEditorAction

    data class TogglePicked(val exerciseId: String) : WorkoutEditorAction

    data object AddPicked : WorkoutEditorAction

    data class RemoveExercise(val workoutExerciseId: String) : WorkoutEditorAction

    data object StartReordering : WorkoutEditorAction

    data object StopReordering : WorkoutEditorAction

    /** Moves the exercise at [from] to [to], positions in the exercise list. */
    data class MoveExercise(val from: Int, val to: Int) : WorkoutEditorAction

    data class ShowExerciseNote(val workoutExerciseId: String) : WorkoutEditorAction

    // Sets
    data class AddSet(val workoutExerciseId: String) : WorkoutEditorAction

    data class RemoveSet(val setId: String) : WorkoutEditorAction

    data class ChangeSetType(val setId: String, val setType: SetType) : WorkoutEditorAction

    /** Leave without saving; handled by the route. The screen confirms first when there are unsaved changes. */
    data object Close : WorkoutEditorAction
}

sealed interface WorkoutEditorEvent {
    data object Saved : WorkoutEditorEvent

    data object Deleted : WorkoutEditorEvent

    data object SaveFailed : WorkoutEditorEvent

    /** Save was tried with no name; the field says so. */
    data object NameMissing : WorkoutEditorEvent

    /** Save was tried with sets missing values; they're marked. */
    data class SetsMissing(val count: Int) : WorkoutEditorEvent

    /** Save was tried with no sets left: the choice is to delete the workout, or keep editing. */
    data object NothingLeft : WorkoutEditorEvent

    /** Exercises were added; the first is the one to bring into view. */
    data class ExercisesAdded(val firstWorkoutExerciseId: String) : WorkoutEditorEvent
}
