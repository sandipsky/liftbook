package com.example.liftbook.ui.feature.exercises

import com.example.liftbook.domain.model.Exercise

data class ArchivedExercisesUiState(
    val isLoading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
)

sealed interface ArchivedExercisesAction {
    data object NavigateUp : ArchivedExercisesAction

    data class OpenExercise(val exerciseId: String) : ArchivedExercisesAction

    data class Restore(val exerciseId: String) : ArchivedExercisesAction
}

sealed interface ArchivedExercisesEvent {
    data class Restored(val name: String) : ArchivedExercisesEvent
}
