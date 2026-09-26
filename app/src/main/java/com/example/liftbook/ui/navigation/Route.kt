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

    /** Every finished workout, as a list or a calendar (FR-4.1, FR-4.4). */
    @Serializable
    data object History : Route

    /** A past workout: every set, with Edit and Delete (FR-4.2). */
    @Serializable
    data class WorkoutDetail(val workoutId: String) : Route

    /** A past workout, opened to edit (FR-4.2). */
    @Serializable
    data class WorkoutEditor(val workoutId: String) : Route

    /** The Progress tab: this week and last, body weight, and each exercise's charts (FR-5). */
    @Serializable
    data object Progress : Route

    /** One exercise's charts over time (FR-5.1). */
    @Serializable
    data class ExerciseProgress(val exerciseId: String) : Route

    /** The body-weight log and its trend (FR-5.4). */
    @Serializable
    data object BodyWeight : Route
}
