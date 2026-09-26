package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import com.example.liftbook.domain.calculator.WorkoutProgress
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.ui.components.ExercisePickerUiState
import java.time.ZoneId

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means no workout is in progress. */
    val workout: Workout? = null,
    /** The workout's exercises, in the order shown — which, mid-drag, is ahead of the database. */
    val exercises: List<ActiveExercise> = emptyList(),
    /** The workout's note (FR-3.9); null until the workout has loaded. */
    val workoutNote: TextFieldState? = null,
    val progress: WorkoutProgress = WorkoutProgress.None,
    /** The rest running now (FR-3.5). It may already be over; the screen decides what that shows. */
    val rest: RestTimer? = null,
    val defaultRestSeconds: Int = UserPreferences.DEFAULT_REST_SECONDS,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** The zone the start time is shown in. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Exercises shown as a compact list to drag into a new order (FR-3.2). */
    val isReordering: Boolean = false,
    /** The exercise picker, while it's open (FR-3.2). */
    val picker: ExercisePickerUiState? = null,
)

/** An exercise in the workout, with its sets ready to edit. */
data class ActiveExercise(
    val item: WorkoutExercise,
    val sets: List<ActiveSet>,
    val note: TextFieldState,
    /** A note field shows once there's a note, or once the user asks to add one. */
    val showNote: Boolean,
    /** How long the rest after each of its sets is; 0 for none (FR-3.5). */
    val restSeconds: Int,
    /** Whether the exercise has a rest time of its own, rather than the default. */
    val hasOwnRest: Boolean,
) {
    val id: String get() = item.id
    val hasCompletedSets: Boolean get() = sets.any { it.set.isCompleted }
}

data class ActiveSet(
    val set: WorkoutSet,
    /** Its working-set number; null for a warm-up, which isn't counted (FR-3.10). */
    val number: Int?,
    val fields: SetFields,
    /** Marking it done was tried with something missing: the empty fields show as errors. */
    val showMissing: Boolean = false,
)

sealed interface ActiveWorkoutAction {
    data object NavigateUp : ActiveWorkoutAction

    /** Delete the workout, once the screen has confirmed it (FR-3.8). */
    data object Discard : ActiveWorkoutAction

    /** Finish it, once the screen has confirmed any sets left undone will be dropped (FR-3.8). */
    data object Finish : ActiveWorkoutAction

    /** Save what's been typed now, as the screen goes out of view (FR-3.7). */
    data object SaveNow : ActiveWorkoutAction

    // Exercises (FR-3.2)
    data object OpenPicker : ActiveWorkoutAction

    data object ClosePicker : ActiveWorkoutAction

    data class TogglePicked(val exerciseId: String) : ActiveWorkoutAction

    data object AddPicked : ActiveWorkoutAction

    data class RemoveExercise(val workoutExerciseId: String) : ActiveWorkoutAction

    data object StartReordering : ActiveWorkoutAction

    data object StopReordering : ActiveWorkoutAction

    /** Moves the exercise at [from] to [to], positions in the exercise list. */
    data class MoveExercise(val from: Int, val to: Int) : ActiveWorkoutAction

    data class ShowExerciseNote(val workoutExerciseId: String) : ActiveWorkoutAction

    // Sets (FR-3.3, FR-3.10)
    data class AddSet(val workoutExerciseId: String) : ActiveWorkoutAction

    data class RemoveSet(val setId: String) : ActiveWorkoutAction

    data class ChangeSetType(val setId: String, val setType: SetType) : ActiveWorkoutAction

    /** Marks the set done — starting the rest timer — or, if it's done, not done. */
    data class ToggleSetDone(val setId: String) : ActiveWorkoutAction

    // Rest timer (FR-3.5)
    data object SkipRest : ActiveWorkoutAction

    data class AdjustRest(val seconds: Int) : ActiveWorkoutAction

    /** Null returns the exercise to the default rest. */
    data class SetExerciseRest(val workoutExerciseId: String, val seconds: Int?) : ActiveWorkoutAction

    data class SetDefaultRest(val seconds: Int) : ActiveWorkoutAction
}

sealed interface ActiveWorkoutEvent {
    data object Discarded : ActiveWorkoutEvent

    data class Finished(val workoutId: String) : ActiveWorkoutEvent

    /** Exercises were added; the first is the one to bring into view. */
    data class ExercisesAdded(val firstWorkoutExerciseId: String) : ActiveWorkoutEvent

    data object SaveFailed : ActiveWorkoutEvent
}
