package com.example.liftbook.ui.feature.progress

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.bodyWeightTrend
import com.example.liftbook.domain.calculator.change
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.repository.BodyWeightRepository
import com.example.liftbook.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The body-weight log (FR-5.4): the weigh-ins over the chosen range with their trend, and logging,
 * changing and deleting them. The trend at each weigh-in averages the week before it, whether or
 * not that week is in range, so the line doesn't bend at the range's edge.
 */
@HiltViewModel
class BodyWeightViewModel @Inject constructor(
    bodyWeightRepository: BodyWeightRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val logger = BodyWeightLogger(bodyWeightRepository, viewModelScope, today = { LocalDate.now(clock) })

    /** The weight being typed into the log sheet. */
    val logWeight: TextFieldState get() = logger.weight

    val events: Flow<BodyWeightLogEvent> = logger.events

    val uiState: StateFlow<BodyWeightUiState> = combine(
        bodyWeightRepository.observeEntries(),
        settingsRepository.userPreferences,
        savedStateHandle.getStateFlow(KEY_RANGE, DEFAULT_RANGE.name),
        logger.state,
    ) { entries, preferences, rangeName, log ->
        logger.update(entries, preferences.weightUnit)
        val today = LocalDate.now(clock)
        val range = ProgressRange.entries.firstOrNull { it.name == rangeName } ?: DEFAULT_RANGE
        val start = range.startOn(today)
        val trend = bodyWeightTrend(entries)
        val inRange = entries.filter { start == null || !it.date.isBefore(start) }
        val trendInRange = trend.filter { start == null || !it.date.isBefore(start) }
        BodyWeightUiState(
            isLoading = false,
            range = range,
            entries = inRange,
            trend = trendInRange,
            change = trendInRange.change(),
            hasEntries = entries.isNotEmpty(),
            weightUnit = preferences.weightUnit,
            today = today,
            log = log,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyWeightUiState(today = LocalDate.now(clock)))

    fun onAction(action: BodyWeightAction) {
        when (action) {
            is BodyWeightAction.SelectRange -> savedStateHandle[KEY_RANGE] = action.range.name
            BodyWeightAction.Log -> logger.open()
            is BodyWeightAction.Edit -> logger.edit(action.entry)
            is BodyWeightAction.ChangeLogDate -> logger.changeDate(action.date)
            BodyWeightAction.SaveLog -> logger.save()
            BodyWeightAction.DeleteLogged -> logger.delete()
            BodyWeightAction.DismissLog -> logger.dismiss()
            is BodyWeightAction.Restore -> logger.restore(action.entry)
            // Navigation; handled by the route.
            BodyWeightAction.NavigateUp -> Unit
        }
    }

    private companion object {
        const val KEY_RANGE = "range"
        val DEFAULT_RANGE = ProgressRange.THREE_MONTHS
    }
}
