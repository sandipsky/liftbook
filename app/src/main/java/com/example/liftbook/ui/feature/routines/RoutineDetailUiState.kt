package com.example.liftbook.ui.feature.routines

import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.WeightUnit
import java.time.LocalDate
import java.time.ZoneId

data class RoutineDetailUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means no routine has this id — it was deleted, say. */
    val routine: Routine? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    /** The zone workout start times are read in, to place them on a calendar day. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /** A start is on its way to the database; Start ignores further taps until it lands. */
    val isStarting: Boolean = false,
)

sealed interface RoutineDetailAction {
    data object NavigateUp : RoutineDetailAction

    data object Edit : RoutineDetailAction

    data class OpenExercise(val exerciseId: String) : RoutineDetailAction

    data object Start : RoutineDetailAction

    /** Go to the workout already in progress, from the "still in progress" dialog. */
    data object ResumeWorkout : RoutineDetailAction

    data object DismissOtherWorkout : RoutineDetailAction

    data object Duplicate : RoutineDetailAction

    /** Delete, once the screen has confirmed it. */
    data object Delete : RoutineDetailAction

    data class OpenRoutine(val routineId: String) : RoutineDetailAction
}

sealed interface RoutineDetailEvent {
    /** A workout from this routine is in progress — just started, or resumed. */
    data object OpenWorkout : RoutineDetailEvent

    data class OtherWorkoutActive(val workoutName: String) : RoutineDetailEvent

    data object StartFailed : RoutineDetailEvent

    data class Duplicated(val routineId: String, val name: String) : RoutineDetailEvent

    data object DuplicateFailed : RoutineDetailEvent

    data object Deleted : RoutineDetailEvent
}
