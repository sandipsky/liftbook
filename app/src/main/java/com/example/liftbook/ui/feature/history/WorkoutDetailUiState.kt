package com.example.liftbook.ui.feature.history

import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutSummary
import com.example.liftbook.ui.components.RecapExercise
import java.time.LocalDate
import java.time.ZoneId

data class WorkoutDetailUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means there's no finished workout with this id. */
    val workout: Workout? = null,
    /** Its totals, and the records it set against every workout before it (FR-5.2). */
    val summary: WorkoutSummary? = null,
    /** What was done, exercise by exercise. */
    val exercises: List<RecapExercise> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
    val isDeleting: Boolean = false,
)

sealed interface WorkoutDetailAction {
    /** Delete the workout, once the screen has confirmed it (FR-4.2). */
    data object Delete : WorkoutDetailAction

    // Navigation; handled by the route.
    data object NavigateUp : WorkoutDetailAction

    data object Edit : WorkoutDetailAction

    data class OpenExercise(val exerciseId: String) : WorkoutDetailAction
}

sealed interface WorkoutDetailEvent {
    data object Deleted : WorkoutDetailEvent

    data object DeleteFailed : WorkoutDetailEvent
}
