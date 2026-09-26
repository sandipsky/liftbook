package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.RoutineDraft
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {

    /** Every routine, sorted by name, each with its exercises and last-performed date (FR-2.4). */
    fun observeRoutines(): Flow<List<Routine>>

    /** Emits null if no routine has this id — for example once it's deleted. */
    fun observeRoutine(id: String): Flow<Routine?>

    suspend fun getRoutine(id: String): Routine?

    /** Creates a routine and returns its id (FR-2.1). */
    suspend fun createRoutine(draft: RoutineDraft): String

    /**
     * Replaces a routine's name and exercises with [draft], in the draft's order (FR-2.2).
     * Exercises that carry their original id keep their row.
     */
    suspend fun updateRoutine(id: String, draft: RoutineDraft)

    /**
     * Copies a routine, targets and all, under the next free name — "Push" becomes "Push 2" —
     * and returns the copy's id (FR-2.2).
     */
    suspend fun duplicateRoutine(id: String): String

    /** Deletes a routine. Workouts started from it stay in history, no longer linked to it (FR-2.2). */
    suspend fun deleteRoutine(id: String)
}
