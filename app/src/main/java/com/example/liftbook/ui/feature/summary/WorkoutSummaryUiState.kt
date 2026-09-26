package com.example.liftbook.ui.feature.summary

import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutSummary
import com.example.liftbook.ui.components.RecapExercise
import java.time.LocalDate
import java.time.ZoneId

data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means there's no finished workout with this id. */
    val workout: Workout? = null,
    val summary: WorkoutSummary? = null,
    /** What was done, exercise by exercise: completed sets only. */
    val exercises: List<RecapExercise> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed interface WorkoutSummaryAction {
    data object Done : WorkoutSummaryAction
}
