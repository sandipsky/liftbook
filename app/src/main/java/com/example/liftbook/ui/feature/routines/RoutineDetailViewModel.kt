package com.example.liftbook.ui.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** One routine: its exercises and targets, when it was last done, and the way to start it (FR-2.2–2.4). */
@HiltViewModel(assistedFactory = RoutineDetailViewModel.Factory::class)
class RoutineDetailViewModel @AssistedInject constructor(
    @Assisted private val routineId: String,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val isStarting = MutableStateFlow(false)

    val uiState: StateFlow<RoutineDetailUiState> = combine(
        routineRepository.observeRoutine(routineId),
        settingsRepository.userPreferences,
        isStarting,
    ) { routine, preferences, starting ->
        RoutineDetailUiState(
            isLoading = false,
            routine = routine,
            weightUnit = preferences.weightUnit,
            today = LocalDate.now(clock),
            zone = clock.zone,
            isStarting = starting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailUiState())

    private val _events = Channel<RoutineDetailEvent>(Channel.BUFFERED)
    val events: Flow<RoutineDetailEvent> = _events.receiveAsFlow()

    fun onAction(action: RoutineDetailAction) {
        when (action) {
            RoutineDetailAction.Start -> start()
            RoutineDetailAction.Duplicate -> viewModelScope.launch {
                try {
                    val copyId = routineRepository.duplicateRoutine(routineId)
                    val copy = routineRepository.getRoutine(copyId) ?: return@launch
                    _events.send(RoutineDetailEvent.Duplicated(copy.id, copy.name))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _events.send(RoutineDetailEvent.DuplicateFailed)
                }
            }
            RoutineDetailAction.Delete -> viewModelScope.launch {
                routineRepository.deleteRoutine(routineId)
                _events.send(RoutineDetailEvent.Deleted)
            }
            // Navigation; handled by the route.
            RoutineDetailAction.NavigateUp,
            RoutineDetailAction.Edit,
            RoutineDetailAction.ResumeWorkout,
            RoutineDetailAction.DismissOtherWorkout,
            is RoutineDetailAction.OpenExercise,
            is RoutineDetailAction.OpenRoutine,
            -> Unit
        }
    }

    private fun start() {
        // Set before anything suspends, so a double tap can't start twice.
        if (isStarting.value) return
        isStarting.value = true
        viewModelScope.launch {
            val event = try {
                when (val result = workoutRepository.startFromRoutine(routineId)) {
                    is StartWorkoutResult.Started, is StartWorkoutResult.Resumed -> RoutineDetailEvent.OpenWorkout
                    is StartWorkoutResult.OtherWorkoutActive -> RoutineDetailEvent.OtherWorkoutActive(result.name)
                    StartWorkoutResult.RoutineNotFound -> RoutineDetailEvent.StartFailed
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RoutineDetailEvent.StartFailed
            } finally {
                isStarting.value = false
            }
            _events.send(event)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(routineId: String): RoutineDetailViewModel
    }
}
