package com.example.liftbook.ui.feature.home

import com.example.liftbook.domain.model.Routine
import java.time.LocalDate
import java.time.ZoneId

data class HomeUiState(
    val isLoading: Boolean = true,
    /** Sorted by name, each with its last-performed date (FR-2.4). */
    val routines: List<Routine> = emptyList(),
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    /** The zone workout start times are read in, to place them on a calendar day. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed interface HomeAction {
    data class OpenRoutine(val routineId: String) : HomeAction

    data object CreateRoutine : HomeAction

    /** Start a workout from this routine, straight from the list (FR-2.3). */
    data class StartRoutine(val routineId: String) : HomeAction

    /** Start a workout with nothing in it yet, called [name] (FR-3.1). */
    data class StartEmpty(val name: String) : HomeAction

    /** Go to the workout already in progress, from the "still in progress" dialog. */
    data object ResumeWorkout : HomeAction

    data object DismissOtherWorkout : HomeAction

    /** Settings live behind the top bar, not in a fifth tab (architecture §4.1). */
    data object OpenSettings : HomeAction
}

sealed interface HomeEvent {
    /** The workout just started, or the one from this routine that was already in progress. */
    data object OpenWorkout : HomeEvent

    data class OtherWorkoutActive(val workoutName: String) : HomeEvent

    data object StartFailed : HomeEvent

    data object RoutineMissing : HomeEvent
}
