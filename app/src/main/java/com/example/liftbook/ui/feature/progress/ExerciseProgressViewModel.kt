package com.example.liftbook.ui.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.progressSeries
import com.example.liftbook.domain.calculator.summary
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.repository.ExerciseRepository
import com.example.liftbook.domain.repository.ProgressRepository
import com.example.liftbook.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

/**
 * One exercise's charts (FR-5.1): the metric chosen, over the range chosen, one point per
 * finished workout. Every workout the exercise was in is read once and filtered here, so
 * changing the range or the metric is instant — an exercise's history is bounded by how often
 * it's been done. The choice survives the process being killed.
 */
@HiltViewModel(assistedFactory = ExerciseProgressViewModel.Factory::class)
class ExerciseProgressViewModel @AssistedInject constructor(
    @Assisted private val exerciseId: String,
    exerciseRepository: ExerciseRepository,
    progressRepository: ProgressRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val uiState: StateFlow<ExerciseProgressUiState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        progressRepository.observeExerciseWorkouts(exerciseId),
        settingsRepository.userPreferences,
        savedStateHandle.getStateFlow<String?>(KEY_METRIC, null),
        savedStateHandle.getStateFlow(KEY_RANGE, DEFAULT_RANGE.name),
    ) { exercise, workouts, preferences, metricName, rangeName ->
        val today = LocalDate.now(clock)
        if (exercise == null) {
            return@combine ExerciseProgressUiState(isLoading = false, weightUnit = preferences.weightUnit, today = today, zone = clock.zone)
        }
        val metrics = ProgressMetric.forType(exercise.type)
        // A metric chosen before the exercise's type changed may no longer apply.
        val metric = metrics.firstOrNull { it.name == metricName } ?: metrics.first()
        val range = ProgressRange.entries.firstOrNull { it.name == rangeName } ?: DEFAULT_RANGE
        val points = progressSeries(workouts, metric, range, today, clock.zone)
        ExerciseProgressUiState(
            isLoading = false,
            exercise = exercise,
            metrics = metrics,
            metric = metric,
            range = range,
            points = points,
            summary = points.summary(),
            hasHistory = workouts.isNotEmpty(),
            metricHasValues = points.isNotEmpty() || progressSeries(workouts, metric, ProgressRange.ALL, today, clock.zone).isNotEmpty(),
            weightUnit = preferences.weightUnit,
            today = today,
            zone = clock.zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseProgressUiState())

    fun onAction(action: ExerciseProgressAction) {
        when (action) {
            is ExerciseProgressAction.SelectMetric -> savedStateHandle[KEY_METRIC] = action.metric.name
            is ExerciseProgressAction.SelectRange -> savedStateHandle[KEY_RANGE] = action.range.name
            // Navigation; handled by the route.
            ExerciseProgressAction.NavigateUp, is ExerciseProgressAction.OpenWorkout -> Unit
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(exerciseId: String): ExerciseProgressViewModel
    }

    private companion object {
        const val KEY_METRIC = "metric"
        const val KEY_RANGE = "range"

        /** Enough to see a trend, recent enough to be about now. */
        val DEFAULT_RANGE = ProgressRange.THREE_MONTHS
    }
}
