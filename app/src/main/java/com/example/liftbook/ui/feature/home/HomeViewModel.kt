package com.example.liftbook.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The Workout tab: start an empty workout (FR-3.1), or one of the user's routines — each with
 * when it was last done — in one tap (FR-2.3, FR-2.4).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = routineRepository.observeRoutines()
        .map { routines ->
            HomeUiState(isLoading = false, routines = routines, today = LocalDate.now(clock), zone = clock.zone)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    /** A start is on its way to the database; taps meanwhile are ignored so it can't start twice. */
    private var isStarting = false

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.StartRoutine -> start { workoutRepository.startFromRoutine(action.routineId) }
            is HomeAction.StartEmpty -> start { workoutRepository.startEmpty(action.name) }
            // Navigation and dialogs; handled by the route.
            is HomeAction.OpenRoutine,
            HomeAction.CreateRoutine,
            HomeAction.ResumeWorkout,
            HomeAction.DismissOtherWorkout,
            -> Unit
        }
    }

    private fun start(request: suspend () -> StartWorkoutResult) {
        if (isStarting) return
        isStarting = true
        viewModelScope.launch {
            val event = try {
                when (val result = request()) {
                    is StartWorkoutResult.Started, is StartWorkoutResult.Resumed -> HomeEvent.OpenWorkout
                    is StartWorkoutResult.OtherWorkoutActive -> HomeEvent.OtherWorkoutActive(result.name)
                    StartWorkoutResult.RoutineNotFound -> HomeEvent.RoutineMissing
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeEvent.StartFailed
            } finally {
                isStarting = false
            }
            _events.send(event)
        }
    }
}
