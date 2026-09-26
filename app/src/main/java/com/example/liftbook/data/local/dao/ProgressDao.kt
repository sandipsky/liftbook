package com.example.liftbook.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.liftbook.data.local.projection.MuscleSetsRow
import com.example.liftbook.data.local.projection.ProgressSetRow
import com.example.liftbook.data.local.projection.TrainedExerciseRow
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Reads for progress and stats (FR-5). Every query here counts completed working sets of
 * finished workouts only: `isCompleted = 1 AND setType <> 'WARMUP'` (FR-3.10) and
 * `finishedAt IS NOT NULL` — the workout in progress isn't progress yet. Archived exercises are
 * not filtered out; their history stays theirs (FR-1.3).
 */
@Dao
interface ProgressDao {

    /**
     * Every working set of one exercise, oldest workout first (FR-5.1). A single range scan of
     * the (exerciseId, completedAt) index — what the denormalised exerciseId is for (§2.6).
     */
    @Query(
        """
        SELECT s.*, e.type AS exerciseType, w.name AS workoutName, w.startedAt AS workoutStartedAt
        FROM workout_sets AS s
        INNER JOIN workouts AS w ON w.id = s.workoutId
        INNER JOIN exercises AS e ON e.id = s.exerciseId
        WHERE s.exerciseId = :exerciseId AND s.isCompleted = 1 AND s.setType <> 'WARMUP'
            AND w.finishedAt IS NOT NULL
        ORDER BY w.startedAt, w.id, s.completedAt, s.position
        """,
    )
    fun observeExerciseSets(exerciseId: String): Flow<List<ProgressSetRow>>

    /**
     * Working sets per muscle group for each finished workout that started at or after [from]
     * and before [until] (FR-5.3). Grouped here, so a week reads a few dozen rows, not every set.
     */
    @Query(
        """
        SELECT w.startedAt AS startedAt, e.primaryMuscle AS muscle, COUNT(*) AS sets
        FROM workouts AS w
        INNER JOIN workout_sets AS s ON s.workoutId = w.id
        INNER JOIN exercises AS e ON e.id = s.exerciseId
        WHERE w.finishedAt IS NOT NULL AND w.startedAt >= :from AND w.startedAt < :until
            AND s.isCompleted = 1 AND s.setType <> 'WARMUP'
        GROUP BY w.id, e.primaryMuscle
        """,
    )
    fun observeMuscleSets(from: Instant, until: Instant): Flow<List<MuscleSetsRow>>

    /** Every exercise with finished working sets, most recently done first. */
    @Query(
        """
        SELECT e.*, MAX(w.startedAt) AS lastPerformedAt, COUNT(DISTINCT w.id) AS workouts
        FROM workout_sets AS s
        INNER JOIN workouts AS w ON w.id = s.workoutId
        INNER JOIN exercises AS e ON e.id = s.exerciseId
        WHERE s.isCompleted = 1 AND s.setType <> 'WARMUP' AND w.finishedAt IS NOT NULL
        GROUP BY e.id
        ORDER BY lastPerformedAt DESC, e.name COLLATE NOCASE
        """,
    )
    fun observeTrainedExercises(): Flow<List<TrainedExerciseRow>>
}
