package com.example.liftbook.ui.feature.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.summarize
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutSummary
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.ui.components.recapExercises
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

/**
 * What a finished workout added up to (FR-3.8): how long it took, the volume lifted, the sets
 * completed, and the personal records it set against every earlier workout. Everything is
 * derived from the logged sets, so it's right even if the workout is edited later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WorkoutSummaryViewModel.Factory::class)
class WorkoutSummaryViewModel @AssistedInject constructor(
    @Assisted private val workoutId: String,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<WorkoutSummaryUiState> = combine(
        workoutRepository.observeWorkout(workoutId).mapLatest { workout ->
            workout?.takeUnless { it.isActive }?.let { it to summarize(it) }
        },
        settingsRepository.userPreferences,
    ) { finished, preferences ->
        val (workout, summary) = finished ?: return@combine WorkoutSummaryUiState(
            isLoading = false,
            weightUnit = preferences.weightUnit,
            today = LocalDate.now(clock),
            zone = clock.zone,
        )
        WorkoutSummaryUiState(
            isLoading = false,
            workout = workout,
            summary = summary,
            exercises = workout.recapExercises(summary.recordSetIds),
            weightUnit = preferences.weightUnit,
            today = LocalDate.now(clock),
            zone = clock.zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState())

    /** Records are a bonus: if the history can't be read, the summary still shows, without them. */
    private suspend fun summarize(workout: Workout): WorkoutSummary {
        val previous: Map<String, List<LoggedSet>> = try {
            workoutRepository.previousSets(workout.exercises.mapTo(HashSet()) { it.exercise.id }, before = workout.startedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
        return workout.summarize(previous)
    }

    @AssistedFactory
    interface Factory {
        fun create(workoutId: String): WorkoutSummaryViewModel
    }
}
