package com.example.liftbook.domain.repository

import androidx.paging.PagingData
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutEdits
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.domain.model.WorkoutRevision
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Workouts, and every edit to the one in progress. Each edit is written as it happens, so the
 * workout in progress survives the app being killed (FR-3.7, architecture §5.3). A finished
 * workout is edited as a whole instead ([saveRevision]).
 */
interface WorkoutRepository {

    /** The workout in progress, with its exercises and sets, or null when there is none (FR-3.1). */
    fun observeActiveWorkout(): Flow<Workout?>

    /** A workout by id, finished or not. Emits null if there's none with that id. */
    fun observeWorkout(id: String): Flow<Workout?>

    /**
     * Starts a workout from a routine: its exercises in order, each with one set per target set,
     * pre-filled with the routine's targets, and last time's values where the routine leaves one
     * blank (FR-2.3, FR-3.4). Only one workout can be in progress (FR-3.1), so if one already
     * is, nothing is started — unless it came from this same routine, in which case it is the
     * one to resume.
     */
    suspend fun startFromRoutine(routineId: String): StartWorkoutResult

    /**
     * Starts a workout with no exercises yet, called [name] (FR-3.1). Starts nothing while
     * another workout is in progress.
     */
    suspend fun startEmpty(name: String): StartWorkoutResult

    /** Deletes the workout in progress, if [workoutId] is it. A finished workout is never touched. */
    suspend fun discardActiveWorkout(workoutId: String)

    /**
     * Adds exercises to the end of the workout, in order, each with the sets done last time
     * (FR-3.2, FR-3.4). Returns the new workout-exercise ids.
     */
    suspend fun addExercises(workoutId: String, exerciseIds: List<String>): List<String>

    /** Removes an exercise and its sets from the workout (FR-3.2). */
    suspend fun removeExercise(workoutExerciseId: String)

    /**
     * Puts the workout's exercises in the order of [orderedIds] (FR-3.2). Exercises missing
     * from it keep their relative order after the ones listed.
     */
    suspend fun reorderExercises(workoutId: String, orderedIds: List<String>)

    /**
     * Adds a set to the end of an exercise, starting from its last working set's values
     * (FR-3.3). Returns the new set's id.
     */
    suspend fun addSet(workoutExerciseId: String): String

    suspend fun removeSet(setId: String)

    /** Normal, warm-up, drop or failure (FR-3.10). */
    suspend fun setSetType(setId: String, setType: SetType)

    /** Saves what was typed into sets and notes, in one go (FR-3.7, FR-3.9). */
    suspend fun saveEdits(workoutId: String, edits: WorkoutEdits)

    /**
     * Marks a set complete with [values] at [completedAt] (FR-3.3), gives the sets in
     * [carryForward] its values, and starts [rest] — or clears the rest timer when it's null
     * (FR-3.5). All in one transaction.
     */
    suspend fun completeSet(
        setId: String,
        values: SetValues,
        completedAt: Instant,
        rest: RestTimer?,
        carryForward: Map<String, SetValues> = emptyMap(),
    )

    /**
     * Marks a completed set not done. If the rest timer running now was started by completing
     * it, that rest is stopped too; returns whether it was.
     */
    suspend fun uncompleteSet(setId: String): Boolean

    /** Starts, changes or — when [rest] is null — stops the workout's rest timer (FR-3.5). */
    suspend fun setRest(workoutId: String, rest: RestTimer?)

    /**
     * Sets how long to rest after the exercise of [workoutExerciseId] — in this workout and
     * every later one (FR-3.5). Null returns it to the global default.
     */
    suspend fun setExerciseRest(workoutExerciseId: String, seconds: Int?)

    /**
     * Finishes the workout in progress (FR-3.8). Sets not marked complete are dropped, and so
     * are exercises left with no sets and no note. Returns false if [workoutId] isn't the
     * workout in progress.
     */
    suspend fun finishWorkout(workoutId: String, finishedAt: Instant): Boolean

    /**
     * The completed working sets (no warm-ups) of each of [exerciseIds] from finished workouts
     * that started before [before]: what a workout's personal records are measured against
     * (FR-5.2). Exercises never done before are missing from the map.
     */
    suspend fun previousSets(exerciseIds: Set<String>, before: Instant): Map<String, List<LoggedSet>>

    /** Whether a workout is in progress (FR-3.1) — without loading it, for a reminder about to go out (FR-7.5). */
    suspend fun hasActiveWorkout(): Boolean

    /** When the most recent workout finished, or null if none has (FR-7.5). */
    suspend fun lastFinishedAt(): Instant?

    /** Every finished workout, newest first (FR-4.1). Paged: this grows without bound (NFR-2). */
    fun observeFinishedWorkouts(): Flow<PagingData<WorkoutListItem>>

    /** Finished workouts that started at or after [from] and before [until], oldest first (FR-4.4). */
    fun observeFinishedBetween(from: Instant, until: Instant): Flow<List<WorkoutListItem>>

    /**
     * Replaces a finished workout with [revision], in one transaction (FR-4.2). Exercises left
     * with no sets and no note are dropped, as when finishing. Volume and records are derived
     * from the sets, so they follow. Returns false if [workoutId] isn't a finished workout.
     */
    suspend fun saveRevision(workoutId: String, revision: WorkoutRevision): Boolean

    /** Deletes a finished workout with everything in it (FR-4.2). The workout in progress is never touched. */
    suspend fun deleteFinishedWorkout(workoutId: String)
}
