package com.example.liftbook.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.data.local.projection.ExerciseSessionWithSets
import com.example.liftbook.data.local.projection.SetWithExerciseType
import com.example.liftbook.domain.model.SetType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SetDao {

    @Insert
    suspend fun insertAll(sets: List<WorkoutSetEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sets WHERE exerciseId = :exerciseId)")
    suspend fun hasSetsForExercise(exerciseId: String): Boolean

    /** Every finished session of an exercise, newest first (FR-4.3). */
    @Transaction
    @Query(EXERCISE_SESSIONS)
    fun exerciseSessions(exerciseId: String): PagingSource<Int, ExerciseSessionWithSets>

    /** The exercise's last-performed values (FR-1.5). */
    @Transaction
    @Query("$EXERCISE_SESSIONS LIMIT 1")
    fun observeLatestExerciseSession(exerciseId: String): Flow<ExerciseSessionWithSets?>

    /** The last time the exercise was performed, which pre-fills it in a new workout (FR-3.4). */
    @Transaction
    @Query("$EXERCISE_SESSIONS LIMIT 1")
    suspend fun getLatestExerciseSession(exerciseId: String): ExerciseSessionWithSets?

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getById(id: String): WorkoutSetEntity?

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY position, id")
    suspend fun getForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun getForWorkout(workoutId: String): List<WorkoutSetEntity>

    @Update
    suspend fun updateAll(sets: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_sets WHERE id IN (:ids)")
    suspend fun deleteAll(ids: Collection<String>)

    @Query(
        "UPDATE workout_sets SET weightKg = :weightKg, reps = :reps, durationSeconds = :durationSeconds, " +
            "distanceMeters = :distanceMeters WHERE id = :id",
    )
    suspend fun setValues(id: String, weightKg: Double?, reps: Int?, durationSeconds: Int?, distanceMeters: Double?)

    @Query(
        "UPDATE workout_sets SET isCompleted = 1, completedAt = :completedAt, weightKg = :weightKg, reps = :reps, " +
            "durationSeconds = :durationSeconds, distanceMeters = :distanceMeters WHERE id = :id",
    )
    suspend fun complete(
        id: String,
        completedAt: Instant,
        weightKg: Double?,
        reps: Int?,
        durationSeconds: Int?,
        distanceMeters: Double?,
    )

    @Query("UPDATE workout_sets SET isCompleted = 0, completedAt = NULL WHERE id = :id")
    suspend fun uncomplete(id: String)

    @Query("UPDATE workout_sets SET setType = :setType WHERE id = :id")
    suspend fun setType(id: String, setType: SetType)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun delete(id: String)

    /** What finishing a workout drops: the sets never marked complete (FR-3.8). */
    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId AND isCompleted = 0")
    suspend fun deleteIncomplete(workoutId: String)

    /**
     * Completed working sets of the exercises from finished workouts that started before
     * [before]: what personal records are measured against (FR-5.2). Warm-ups are excluded here,
     * at the source, as in every volume and PR query (FR-3.10).
     */
    @Query(
        """
        SELECT s.*, e.type AS exerciseType FROM workout_sets AS s
        INNER JOIN workouts AS w ON w.id = s.workoutId
        INNER JOIN exercises AS e ON e.id = s.exerciseId
        WHERE s.exerciseId IN (:exerciseIds) AND s.isCompleted = 1 AND s.setType <> 'WARMUP'
            AND w.finishedAt IS NOT NULL AND w.startedAt < :before
        """,
    )
    suspend fun previousWorkingSets(exerciseIds: Collection<String>, before: Instant): List<SetWithExerciseType>
}

/*
 * Sessions of one exercise inside finished workouts that have at least one completed set.
 * The active workout (finishedAt IS NULL) is deliberately excluded: history is what was done,
 * not what is in progress. Archived exercises are not filtered out — their history stays
 * readable (FR-1.3).
 */
private const val EXERCISE_SESSIONS = """
    SELECT we.id AS workoutExerciseId, w.id AS workoutId, w.name AS workoutName,
        w.startedAt AS startedAt, e.type AS exerciseType
    FROM workout_exercises AS we
    INNER JOIN workouts AS w ON w.id = we.workoutId
    INNER JOIN exercises AS e ON e.id = we.exerciseId
    WHERE we.exerciseId = :exerciseId
        AND w.finishedAt IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM workout_sets AS s
            WHERE s.workoutExerciseId = we.id AND s.isCompleted = 1
        )
    ORDER BY w.startedAt DESC, we.position ASC, we.id ASC
"""
