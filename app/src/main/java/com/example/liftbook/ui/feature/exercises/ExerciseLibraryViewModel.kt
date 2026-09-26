package com.example.liftbook.ui.feature.exercises

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.normalizeForSearch
import com.example.liftbook.domain.calculator.searchExercises
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseFilter
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The exercise library (FR-1.1, FR-1.4). Search and filters are applied in memory on every
 * change — the library is small enough that this is instant, so there is no debounce to add
 * latency.
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * The search text. Owned by the ViewModel as editable state, not round-tripped through
     * [uiState], so fast typing can never lose characters or move the cursor.
     */
    val queryState = TextFieldState()

    private val muscleFilter = MutableStateFlow<MuscleGroup?>(null)
    private val equipmentFilter = MutableStateFlow<Equipment?>(null)

    private val _events = Channel<ExerciseLibraryEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseLibraryEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        repository.observeLibrary(),
        snapshotFlow { queryState.text.toString() },
        muscleFilter,
        equipmentFilter,
    ) { library, query, muscle, equipment ->
        val filter = ExerciseFilter(query = query, muscleGroup = muscle, equipment = equipment)
        val results = searchExercises(library, filter)
        ExerciseLibraryUiState(
            isLoading = false,
            query = query.trim(),
            muscleFilter = muscle,
            equipmentFilter = equipment,
            sections = if (filter.isEmpty) sectionByInitial(results) else listOf(ExerciseSection(null, results)),
            resultCount = results.size,
            libraryCount = library.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState())

    init {
        // The detail screen hands back the id of an exercise it archived, so the undo
        // snackbar can appear here, where the user lands.
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>(ARCHIVED_EXERCISE_ID, null)
                .filterNotNull()
                .collect { id ->
                    savedStateHandle[ARCHIVED_EXERCISE_ID] = null
                    repository.getExercise(id)?.let {
                        _events.send(ExerciseLibraryEvent.ExerciseArchived(it.id, it.name))
                    }
                }
        }
    }

    fun onAction(action: ExerciseLibraryAction) {
        when (action) {
            is ExerciseLibraryAction.FilterByMuscle -> muscleFilter.value = action.muscleGroup
            is ExerciseLibraryAction.FilterByEquipment -> equipmentFilter.value = action.equipment
            ExerciseLibraryAction.ClearFilters -> {
                muscleFilter.value = null
                equipmentFilter.value = null
            }
            is ExerciseLibraryAction.RestoreExercise -> viewModelScope.launch {
                repository.restore(action.exerciseId)
            }
            // Navigation; handled by the route.
            is ExerciseLibraryAction.OpenExercise,
            is ExerciseLibraryAction.CreateExercise,
            ExerciseLibraryAction.OpenArchived,
            -> Unit
        }
    }

    companion object {
        /** Set on this destination's SavedStateHandle after the detail screen archives an exercise. */
        const val ARCHIVED_EXERCISE_ID = "archivedExerciseId"
    }
}

/** Groups alphabetical results under their initial letter; anything not starting with a letter goes under "#". */
private fun sectionByInitial(exercises: List<Exercise>): List<ExerciseSection> =
    exercises
        .groupBy { exercise ->
            normalizeForSearch(exercise.name).firstOrNull()
                ?.takeIf { it.isLetter() }
                ?.uppercase()
                ?: "#"
        }
        .map { (initial, group) -> ExerciseSection(initial, group) }
