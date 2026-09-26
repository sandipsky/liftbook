package com.example.liftbook.ui.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Archived exercises, and the way back into the library for any of them (FR-1.3). */
@HiltViewModel
class ArchivedExercisesViewModel @Inject constructor(
    private val repository: ExerciseRepository,
) : ViewModel() {

    val uiState: StateFlow<ArchivedExercisesUiState> = repository.observeArchived()
        .map { ArchivedExercisesUiState(isLoading = false, exercises = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedExercisesUiState())

    private val _events = Channel<ArchivedExercisesEvent>(Channel.BUFFERED)
    val events: Flow<ArchivedExercisesEvent> = _events.receiveAsFlow()

    fun onAction(action: ArchivedExercisesAction) {
        when (action) {
            is ArchivedExercisesAction.Restore -> viewModelScope.launch {
                val exercise = repository.getExercise(action.exerciseId) ?: return@launch
                repository.restore(exercise.id)
                _events.send(ArchivedExercisesEvent.Restored(exercise.name))
            }
            // Navigation; handled by the route.
            ArchivedExercisesAction.NavigateUp, is ArchivedExercisesAction.OpenExercise -> Unit
        }
    }
}
