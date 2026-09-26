package com.example.liftbook.ui.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftbook.domain.calculator.progressSeries
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.model.ProgressMetric
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.repository.ExerciseRepository
import com.example.liftbook.domain.repository.ProgressRepository
import com.example.liftbook.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * One exercise, with its last-performed values and full history (FR-1.5, FR-4.3), and a glance
 * at its progress that leads to its charts (FR-5.1).
 */
@HiltViewModel(assistedFactory = ExerciseDetailViewModel.Factory::class)
class ExerciseDetailViewModel @AssistedInject constructor(
    @Assisted private val exerciseId: String,
    private val repository: ExerciseRepository,
    progressRepository: ProgressRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Paged, because this is every session ever logged for the exercise (NFR-2). */
    val history: Flow<PagingData<ExerciseSession>> =
        repository.observeHistory(exerciseId).cachedIn(viewModelScope)

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        repository.observeExercise(exerciseId),
        repository.observeLastSession(exerciseId),
        progressRepository.observeExerciseWorkouts(exerciseId),
        settingsRepository.userPreferences,
    ) { exercise, lastSession, workouts, preferences ->
        val today = LocalDate.now(clock)
        val progress = exercise?.let {
            val metric = ProgressMetric.forType(it.type).first()
            progressSeries(workouts, metric, ProgressRange.ALL, today, clock.zone)
                .takeIf { points -> points.isNotEmpty() }
                ?.let { points -> ProgressPreview(metric, points) }
        }
        ExerciseDetailUiState(
            isLoading = false,
            exercise = exercise,
            lastSession = lastSession,
            progress = progress,
            weightUnit = preferences.weightUnit,
            today = today,
            zone = clock.zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    private val _events = Channel<ExerciseDetailEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseDetailEvent> = _events.receiveAsFlow()

    fun onAction(action: ExerciseDetailAction) {
        when (action) {
            ExerciseDetailAction.Archive -> viewModelScope.launch {
                repository.archive(exerciseId)
                _events.send(ExerciseDetailEvent.Archived(exerciseId))
            }
            ExerciseDetailAction.Restore -> viewModelScope.launch { repository.restore(exerciseId) }
            // Navigation; handled by the route.
            ExerciseDetailAction.NavigateUp,
            ExerciseDetailAction.Edit,
            ExerciseDetailAction.OpenProgress,
            is ExerciseDetailAction.OpenWorkout,
            -> Unit
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(exerciseId: String): ExerciseDetailViewModel
    }
}
