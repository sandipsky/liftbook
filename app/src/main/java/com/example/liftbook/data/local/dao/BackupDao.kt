package com.example.liftbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.data.local.entity.RoutineExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutEntity
import com.example.liftbook.data.local.entity.WorkoutExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutScheduleEntity
import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.data.local.projection.DataCountsRow
import kotlinx.coroutines.flow.Flow

/**
 * Whole-table reads and writes for export, import and clearing all data (FR-6.3–6.5). Callers run
 * these inside one transaction, so an export is a consistent snapshot and an import is all or none.
 */
@Dao
interface BackupDao {

    /** What a backup would hold. Finished workouts only; the one in progress isn't history yet. */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM workouts WHERE finishedAt IS NOT NULL) AS workouts,
            (SELECT COUNT(*) FROM routines) AS routines,
            (SELECT COUNT(*) FROM exercises WHERE isCustom = 1) AS customExercises,
            (SELECT COUNT(*) FROM body_weight_entries) AS weighIns,
            (SELECT COUNT(*) FROM workout_schedules) AS schedules
        """,
    )
    fun observeCounts(): Flow<DataCountsRow>

    @Query("SELECT * FROM exercises ORDER BY createdAt, name")
    suspend fun getExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM routines ORDER BY createdAt")
    suspend fun getRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercises ORDER BY routineId, position")
    suspend fun getRoutineExercises(): List<RoutineExerciseEntity>

    @Query("SELECT * FROM workouts WHERE finishedAt IS NOT NULL ORDER BY startedAt")
    suspend fun getFinishedWorkouts(): List<WorkoutEntity>

    @Query(
        """
        SELECT we.* FROM workout_exercises AS we
        JOIN workouts AS w ON w.id = we.workoutId
        WHERE w.finishedAt IS NOT NULL
        ORDER BY we.workoutId, we.position
        """,
    )
    suspend fun getFinishedWorkoutExercises(): List<WorkoutExerciseEntity>

    @Query(
        """
        SELECT s.* FROM workout_sets AS s
        JOIN workouts AS w ON w.id = s.workoutId
        WHERE w.finishedAt IS NOT NULL
        ORDER BY s.workoutExerciseId, s.position
        """,
    )
    suspend fun getFinishedWorkoutSets(): List<WorkoutSetEntity>

    @Query("SELECT * FROM body_weight_entries ORDER BY recordedOn")
    suspend fun getBodyWeight(): List<BodyWeightEntryEntity>

    @Query("SELECT * FROM workout_schedules ORDER BY startTimeMinutes, createdAt, id")
    suspend fun getSchedules(): List<WorkoutScheduleEntity>

    /** Every workout's id, the one in progress included. */
    @Query("SELECT id FROM workouts")
    suspend fun getWorkoutIds(): List<String>

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    /** For restoring the built-in library after a clear: rows already there are kept as they are. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercisesIfMissing(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Insert
    suspend fun insertRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Insert
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>)

    @Insert
    suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Insert
    suspend fun insertBodyWeight(entries: List<BodyWeightEntryEntity>)

    @Insert
    suspend fun insertSchedules(schedules: List<WorkoutScheduleEntity>)

    // Children before parents: exercises are RESTRICT, so they go only once nothing refers to them.

    @Query("DELETE FROM workout_schedules")
    suspend fun deleteSchedules()

    @Query("DELETE FROM workout_sets")
    suspend fun deleteSets()

    @Query("DELETE FROM workout_exercises")
    suspend fun deleteWorkoutExercises()

    @Query("DELETE FROM workouts")
    suspend fun deleteWorkouts()

    @Query("DELETE FROM routine_exercises")
    suspend fun deleteRoutineExercises()

    @Query("DELETE FROM routines")
    suspend fun deleteRoutines()

    @Query("DELETE FROM body_weight_entries")
    suspend fun deleteBodyWeight()

    @Query("DELETE FROM exercises")
    suspend fun deleteExercises()
}
