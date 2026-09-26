package com.example.liftbook.domain.repository

import androidx.paging.PagingData
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseDraft
import com.example.liftbook.domain.model.ExerciseSession
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {

    /** Every exercise in the library — built-in and custom, not archived — sorted by name. */
    fun observeLibrary(): Flow<List<Exercise>>

    fun observeArchived(): Flow<List<Exercise>>

    /** Emits null if no exercise has this id. Archived exercises are still returned. */
    fun observeExercise(id: String): Flow<Exercise?>

    suspend fun getExercise(id: String): Exercise?

    /** Creates a custom exercise and returns its id (FR-1.2). */
    suspend fun createCustomExercise(draft: ExerciseDraft): String

    /**
     * Updates a custom exercise (FR-1.3).
     *
     * @throws IllegalArgumentException if the exercise is built-in, or if [draft] changes the
     * type of an exercise that already has logged sets — that would change what those sets mean.
     */
    suspend fun updateCustomExercise(id: String, draft: ExerciseDraft)

    /** Removes the exercise from the library. Its logged sets are untouched (FR-1.3). */
    suspend fun archive(id: String)

    suspend fun restore(id: String)

    /** Whether any set, in any workout, references this exercise. */
    suspend fun hasLoggedSets(id: String): Boolean

    /** Every finished session of this exercise, newest first (FR-1.5, FR-4.3). */
    fun observeHistory(exerciseId: String): Flow<PagingData<ExerciseSession>>

    /** The most recent finished session: the exercise's last-performed values (FR-1.5). */
    fun observeLastSession(exerciseId: String): Flow<ExerciseSession?>
}
