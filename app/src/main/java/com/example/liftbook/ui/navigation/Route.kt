package com.example.liftbook.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations: arguments are checked at compile time, never string-encoded. */
sealed interface Route {

    /** The Workout tab: routines, and starting a workout from one. */
    @Serializable
    data object Home : Route

    @Serializable
    data object ExerciseLibrary : Route

    @Serializable
    data object ArchivedExercises : Route

    @Serializable
    data class ExerciseDetail(val exerciseId: String) : Route

    /** A null [exerciseId] creates a custom exercise; [initialName] prefills its name. */
    @Serializable
    data class ExerciseEditor(
        val exerciseId: String? = null,
        val initialName: String? = null,
    ) : Route

    @Serializable
    data class RoutineDetail(val routineId: String) : Route

    /** A null [routineId] creates a routine. */
    @Serializable
    data class RoutineEditor(val routineId: String? = null) : Route

    /** The workout in progress, full screen. There is at most one, so it takes no argument. */
    @Serializable
    data object ActiveWorkout : Route

    /** What a workout just finished added up to (FR-3.8). */
    @Serializable
    data class WorkoutSummary(val workoutId: String) : Route
}
