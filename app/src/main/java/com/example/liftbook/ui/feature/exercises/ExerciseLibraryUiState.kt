package com.example.liftbook.ui.feature.exercises

import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.MuscleGroup

data class ExerciseLibraryUiState(
    val isLoading: Boolean = true,
    /** The search text as last applied, trimmed. */
    val query: String = "",
    val muscleFilter: MuscleGroup? = null,
    val equipmentFilter: Equipment? = null,
    /** Alphabetical sections when browsing; a single untitled section of ranked matches when searching or filtering. */
    val sections: List<ExerciseSection> = emptyList(),
    val resultCount: Int = 0,
    /** Every exercise in the library, before search and filters. */
    val libraryCount: Int = 0,
) {
    val hasFilters: Boolean get() = muscleFilter != null || equipmentFilter != null
    val isFiltering: Boolean get() = query.isNotEmpty() || hasFilters
}

data class ExerciseSection(
    /** The initial letter heading this section, or null for unsectioned results. */
    val title: String?,
    val exercises: List<Exercise>,
)

sealed interface ExerciseLibraryAction {
    data class OpenExercise(val exerciseId: String) : ExerciseLibraryAction

    /** Opens the editor for a new custom exercise, prefilled with [name] when there is one. */
    data class CreateExercise(val name: String?) : ExerciseLibraryAction

    data object OpenArchived : ExerciseLibraryAction

    data class FilterByMuscle(val muscleGroup: MuscleGroup?) : ExerciseLibraryAction

    data class FilterByEquipment(val equipment: Equipment?) : ExerciseLibraryAction

    data object ClearFilters : ExerciseLibraryAction

    data class RestoreExercise(val exerciseId: String) : ExerciseLibraryAction
}

sealed interface ExerciseLibraryEvent {
    /** An exercise was archived from its detail screen; offer to undo it. */
    data class ExerciseArchived(val exerciseId: String, val name: String) : ExerciseLibraryEvent
}
