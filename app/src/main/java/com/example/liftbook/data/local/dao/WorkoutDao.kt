package com.example.liftbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.liftbook.data.local.entity.WorkoutEntity
import com.example.liftbook.data.local.entity.WorkoutExerciseEntity
import com.example.liftbook.data.local.projection.WorkoutWithExercises
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WorkoutDao {

    /** The workout in progress (FR-3.1). The repository keeps it to at most one. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutWithExercises?>

    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeById(id: String): Flow<WorkoutWithExercises?>

    @Insert
    suspend fun insert(workout: WorkoutEntity)

    @Insert
    suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>)

    /** Its exercises and their sets go with it (CASCADE). */
    @Query("DELETE FROM workouts WHERE id = :id AND finishedAt IS NULL")
    suspend fun deleteIfActive(id: String)

    @Query("UPDATE workouts SET note = :note WHERE id = :id")
    suspend fun setNote(id: String, note: String?)

    @Query("UPDATE workouts SET restStartedAt = :startedAt, restEndsAt = :endsAt WHERE id = :id")
    suspend fun setRest(id: String, startedAt: Instant?, endsAt: Instant?)

    @Query("SELECT restStartedAt FROM workouts WHERE id = :id")
    suspend fun getRestStartedAt(id: String): Instant?

    /** Returns the number of rows finished: 0 unless it was the workout in progress. */
    @Query(
        "UPDATE workouts SET finishedAt = :finishedAt, restStartedAt = NULL, restEndsAt = NULL " +
            "WHERE id = :id AND finishedAt IS NULL",
    )
    suspend fun finish(id: String, finishedAt: Instant): Int

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    suspend fun getExercise(id: String): WorkoutExerciseEntity?

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position, id")
    suspend fun getExercises(workoutId: String): List<WorkoutExerciseEntity>

    /** -1 when the workout has no exercises yet. */
    @Query("SELECT IFNULL(MAX(position), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun maxExercisePosition(workoutId: String): Int

    @Query("UPDATE workout_exercises SET position = :position WHERE id = :id")
    suspend fun setExercisePosition(id: String, position: Int)

    @Query("UPDATE workout_exercises SET note = :note WHERE id = :id")
    suspend fun setExerciseNote(id: String, note: String?)

    @Query("UPDATE workout_exercises SET restSecondsOverride = :seconds WHERE id = :id")
    suspend fun setExerciseRestOverride(id: String, seconds: Int?)

    /** Its sets go with it (CASCADE). */
    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    /** Exercises of the workout with no sets left, unless the user wrote a note on one. */
    @Query(
        """
        DELETE FROM workout_exercises
        WHERE workoutId = :workoutId AND note IS NULL
            AND NOT EXISTS (SELECT 1 FROM workout_sets AS s WHERE s.workoutExerciseId = workout_exercises.id)
        """,
    )
    suspend fun deleteEmptyExercises(workoutId: String)
}
