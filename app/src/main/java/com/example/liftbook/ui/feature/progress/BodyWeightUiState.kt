package com.example.liftbook.ui.feature.progress

import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.TrendPoint
import com.example.liftbook.domain.model.WeightUnit
import java.time.LocalDate

data class BodyWeightUiState(
    val isLoading: Boolean = true,
    val range: ProgressRange = ProgressRange.THREE_MONTHS,
    /** Weigh-ins in the range, oldest first. */
    val entries: List<BodyWeightEntry> = emptyList(),
    /** The trend at each of [entries] (FR-5.4), smoothed over the days before each, in range or not. */
    val trend: List<TrendPoint> = emptyList(),
    /** How far the trend moved across the range; null with fewer than two weigh-ins in it. */
    val change: Double? = null,
    /** Whether there's a weigh-in at all, in range or not. */
    val hasEntries: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    /** The weigh-in being logged or changed, while its sheet is open. */
    val log: BodyWeightLogState? = null,
)

sealed interface BodyWeightAction {
    data class SelectRange(val range: ProgressRange) : BodyWeightAction

    // The log sheet (FR-5.4)
    data object Log : BodyWeightAction

    data class Edit(val entry: BodyWeightEntry) : BodyWeightAction

    data class ChangeLogDate(val date: LocalDate) : BodyWeightAction

    data object SaveLog : BodyWeightAction

    data object DeleteLogged : BodyWeightAction

    data object DismissLog : BodyWeightAction

    data class Restore(val entry: BodyWeightEntry) : BodyWeightAction

    // Navigation; handled by the route.
    data object NavigateUp : BodyWeightAction
}
