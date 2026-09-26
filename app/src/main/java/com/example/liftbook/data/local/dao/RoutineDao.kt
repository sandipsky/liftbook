package com.example.liftbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.data.local.entity.RoutineExerciseEntity
import com.example.liftbook.data.local.projection.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Transaction
    @Query("$ROUTINES ORDER BY r.name COLLATE NOCASE, r.createdAt")
    fun observeAll(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("$ROUTINES WHERE r.id = :id")
    fun observeById(id: String): Flow<RoutineWithExercises?>

    @Transaction
    @Query("$ROUTINES WHERE r.id = :id")
    suspend fun getById(id: String): RoutineWithExercises?

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getEntity(id: String): RoutineEntity?

    @Query("SELECT name FROM routines")
    suspend fun getNames(): List<String>

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY position")
    suspend fun getExercises(routineId: String): List<RoutineExerciseEntity>

    @Insert
    suspend fun insert(routine: RoutineEntity)

    @Update
    suspend fun update(routine: RoutineEntity)

    @Insert
    suspend fun insertExercises(exercises: List<RoutineExerciseEntity>)

    @Update
    suspend fun updateExercises(exercises: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE id IN (:ids)")
    suspend fun deleteExercises(ids: Collection<String>)

    /** Its exercises go with it (CASCADE); workouts started from it are kept, unlinked (SET NULL). */
    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: String)
}

/*
 * Routines with their last-performed time (FR-2.4): the start of the latest *finished* workout
 * from each. A workout still in progress hasn't been performed yet. The subquery uses the
 * index on workouts.routineId, and is derived on every read so it can't go stale when history
 * is edited or deleted.
 */
private const val ROUTINES = """
    SELECT r.*, (
        SELECT MAX(w.startedAt) FROM workouts AS w
        WHERE w.routineId = r.id AND w.finishedAt IS NOT NULL
    ) AS lastPerformedAt
    FROM routines AS r
"""
