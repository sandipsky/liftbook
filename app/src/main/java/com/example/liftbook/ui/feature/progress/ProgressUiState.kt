package com.example.liftbook.ui.feature.progress

import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.TrainedExercise
import com.example.liftbook.domain.model.TrendPoint
import com.example.liftbook.domain.model.WeeklySummary
import com.example.liftbook.domain.model.WeightUnit
import java.time.LocalDate
import java.time.ZoneId

data class ProgressUiState(
    val isLoading: Boolean = true,
    /** This week and last (FR-5.3); null until loaded. */
    val week: WeeklySummary? = null,
    /** Every exercise with finished work, most recently done first: each opens its charts (FR-5.1). */
    val exercises: List<TrainedExercise> = emptyList(),
    /** The latest weigh-in and the recent trend (FR-5.4); null before the first weigh-in. */
    val bodyWeight: BodyWeightGlance? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
    /** The weigh-in being logged, while its sheet is open. */
    val log: BodyWeightLogState? = null,
) {
    /** Nothing has been finished yet, so there's nothing to chart but body weight. */
    val hasNoWorkouts: Boolean get() = exercises.isEmpty()
}

/** Body weight at a glance: the latest weigh-in, and the trend over the last few months. */
data class BodyWeightGlance(
    val latest: BodyWeightEntry,
    /** Oldest first; empty when every weigh-in is older than the glance looks back. */
    val trend: List<TrendPoint>,
    /** How far [trend] moved; null with fewer than two points. */
    val change: Double?,
)

sealed interface ProgressAction {
    // The log sheet (FR-5.4)
    data object LogBodyWeight : ProgressAction

    data class ChangeLogDate(val date: LocalDate) : ProgressAction

    data object SaveLog : ProgressAction

    data object DismissLog : ProgressAction

    // Navigation; handled by the route.
    data class OpenExercise(val exerciseId: String) : ProgressAction

    data object OpenBodyWeight : ProgressAction

    /** From the empty state: go to where a workout starts. */
    data object StartWorkout : ProgressAction
}
