package com.example.liftbook.ui.feature.progress

import com.example.liftbook.domain.calculator.SeriesSummary
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressPoint
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.WeightUnit
import java.time.LocalDate
import java.time.ZoneId

data class ExerciseProgressUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means no exercise has this id. */
    val exercise: Exercise? = null,
    /** What its type can chart, the default first (FR-5.1). */
    val metrics: List<ProgressMetric> = emptyList(),
    val metric: ProgressMetric = ProgressMetric.ESTIMATED_ONE_REP_MAX,
    val range: ProgressRange = ProgressRange.THREE_MONTHS,
    /** The metric over the range, one per workout, oldest first. */
    val points: List<ProgressPoint> = emptyList(),
    val summary: SeriesSummary? = null,
    /** Whether any finished workout has the exercise at all. */
    val hasHistory: Boolean = false,
    /** Whether the metric has a value in any workout, in range or not. */
    val metricHasValues: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed interface ExerciseProgressAction {
    data class SelectMetric(val metric: ProgressMetric) : ExerciseProgressAction

    data class SelectRange(val range: ProgressRange) : ExerciseProgressAction

    // Navigation; handled by the route.
    data object NavigateUp : ExerciseProgressAction

    /** Opens a workout the chart has a point for (FR-4.2). */
    data class OpenWorkout(val workoutId: String) : ExerciseProgressAction
}
