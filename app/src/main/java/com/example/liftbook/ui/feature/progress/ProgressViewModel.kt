package com.example.liftbook.ui.feature.progress

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.bodyWeightTrend
import com.example.liftbook.domain.calculator.change
import com.example.liftbook.domain.calculator.summaryWeeks
import com.example.liftbook.domain.calculator.weeklySummary
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.WeeklySummary
import com.example.liftbook.domain.repository.BodyWeightRepository
import com.example.liftbook.domain.repository.ProgressRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import javax.inject.Inject

/**
 * The Progress tab (FR-5): this week against last (FR-5.3), body weight at a glance with a way to
 * log it (FR-5.4), and every exercise that has been done, each leading to its charts (FR-5.1).
 * Everything is worked out from logged sets as they change, so a workout finished or edited
 * shows here straight away.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    progressRepository: ProgressRepository,
    bodyWeightRepository: BodyWeightRepository,
    settingsRepository: SettingsRepository,
    weekFields: WeekFields,
    private val clock: Clock,
) : ViewModel() {

    private val logger = BodyWeightLogger(bodyWeightRepository, viewModelScope, today = { LocalDate.now(clock) })

    /** The weight being typed into the log sheet. */
    val logWeight: TextFieldState get() = logger.weight

    val events: Flow<BodyWeightLogEvent> = logger.events

    private val today = LocalDate.now(clock)

    private val week: Flow<WeeklySummary> = run {
        val firstDayOfWeek = weekFields.firstDayOfWeek
        val span = summaryWeeks(today, firstDayOfWeek, clock.zone)
        combine(
            workoutRepository.observeFinishedBetween(span.from, span.until),
            progressRepository.observeMuscleSets(span.from, span.until),
        ) { workouts, muscleSets -> weeklySummary(workouts, muscleSets, today, firstDayOfWeek, clock.zone) }
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        week,
        progressRepository.observeTrainedExercises(),
        bodyWeightRepository.observeEntries(),
        settingsRepository.userPreferences,
        logger.state,
    ) { week, exercises, entries, preferences, log ->
        logger.update(entries, preferences.weightUnit)
        ProgressUiState(
            isLoading = false,
            week = week,
            exercises = exercises,
            bodyWeight = glance(entries),
            weightUnit = preferences.weightUnit,
            today = today,
            zone = clock.zone,
            log = log,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState(today = today, zone = clock.zone))

    fun onAction(action: ProgressAction) {
        when (action) {
            ProgressAction.LogBodyWeight -> logger.open()
            is ProgressAction.ChangeLogDate -> logger.changeDate(action.date)
            ProgressAction.SaveLog -> logger.save()
            ProgressAction.DismissLog -> logger.dismiss()
            // Navigation; handled by the route.
            is ProgressAction.OpenExercise, ProgressAction.OpenBodyWeight, ProgressAction.StartWorkout -> Unit
        }
    }

    private fun glance(entries: List<BodyWeightEntry>): BodyWeightGlance? {
        val latest = entries.maxByOrNull { it.date } ?: return null
        val start = GLANCE_RANGE.startOn(today)
        val trend = bodyWeightTrend(entries).filter { start == null || !it.date.isBefore(start) }
        return BodyWeightGlance(latest = latest, trend = trend, change = trend.change())
    }

    private companion object {
        val GLANCE_RANGE = ProgressRange.THREE_MONTHS
    }
}
