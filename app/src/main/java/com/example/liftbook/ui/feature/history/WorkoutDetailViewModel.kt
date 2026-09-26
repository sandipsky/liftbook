package com.example.liftbook.ui.feature.history

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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * A past workout (FR-4.2): every set it logged, what it added up to, and the records it set at
 * the time. All of it is worked out from the sets whenever they change, so an edit — to this
 * workout or an earlier one — shows here as soon as it's saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WorkoutDetailViewModel.Factory::class)
class WorkoutDetailViewModel @AssistedInject constructor(
    @Assisted private val workoutId: String,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val deleting = MutableStateFlow(false)

    private val _events = Channel<WorkoutDetailEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutDetailEvent> = _events.receiveAsFlow()

    /** The workout as last shown, kept on screen while it's being deleted. */
    private var shown: Pair<Workout, WorkoutSummary>? = null

    val uiState: StateFlow<WorkoutDetailUiState> = combine(
        workoutRepository.observeWorkout(workoutId).mapLatest { workout ->
            workout?.takeUnless { it.isActive }?.let { it to summarize(it) }
        },
        settingsRepository.userPreferences,
        deleting,
    ) { finished, preferences, isDeleting ->
        // Once deleted, keep showing it until the screen closes, instead of flashing "not found".
        val current = finished ?: shown?.takeIf { isDeleting }
        shown = current
        WorkoutDetailUiState(
            isLoading = false,
            workout = current?.first,
            summary = current?.second,
            exercises = current?.first?.recapExercises().orEmpty(),
            weightUnit = preferences.weightUnit,
            today = LocalDate.now(clock),
            zone = clock.zone,
            isDeleting = isDeleting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState())

    fun onAction(action: WorkoutDetailAction) {
        when (action) {
            WorkoutDetailAction.Delete -> delete()
            // Navigation; handled by the route.
            WorkoutDetailAction.NavigateUp, WorkoutDetailAction.Edit, is WorkoutDetailAction.OpenExercise -> Unit
        }
    }

    private fun delete() {
        if (deleting.value) return
        deleting.value = true
        viewModelScope.launch {
            try {
                workoutRepository.deleteFinishedWorkout(workoutId)
                _events.send(WorkoutDetailEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deleting.value = false
                _events.send(WorkoutDetailEvent.DeleteFailed)
            }
        }
    }

    /** Records are a bonus: if the history can't be read, the workout still shows, without them. */
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
        fun create(workoutId: String): WorkoutDetailViewModel
    }
}
