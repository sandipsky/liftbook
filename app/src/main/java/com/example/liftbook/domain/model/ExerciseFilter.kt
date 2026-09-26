package com.example.liftbook.domain.model

/** What the user is looking for in the exercise library (FR-1.4). */
data class ExerciseFilter(
    val query: String = "",
    val muscleGroup: MuscleGroup? = null,
    val equipment: Equipment? = null,
) {
    val isEmpty: Boolean get() = query.isBlank() && muscleGroup == null && equipment == null
}
