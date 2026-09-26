package com.example.liftbook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftbook.domain.model.SetType
import java.time.Instant

/**
 * A logged set. [workoutId] and [exerciseId] are denormalised copies of what the parent
 * workout-exercise already knows, so the two hottest queries — every set for an exercise, and
 * a workout's total volume — are single index scans instead of three-table joins. They can't
 * drift: a set never moves between workout-exercises. Only the code that creates sets writes
 * them.
 */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("workoutExerciseId", "position"),
        Index("exerciseId", "completedAt"),
        // For per-workout aggregates such as total volume (FR-4.1).
        Index("workoutId"),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
    val setType: SetType,
    val isCompleted: Boolean,
    /** Kilograms — the only unit ever stored (FR-6.1). For bodyweight sets, any added weight. */
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val completedAt: Instant?,
)
