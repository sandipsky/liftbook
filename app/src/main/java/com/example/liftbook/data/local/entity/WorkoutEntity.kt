package com.example.liftbook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftbook.domain.model.WorkoutMode
import java.time.Instant

/**
 * A workout. `finishedAt IS NULL` means it is the active workout — there is no status column
 * and no duration column (duration is `finishedAt - startedAt`).
 *
 * The rest timer (FR-3.5) is the two instants that bound it rather than a countdown, so it
 * survives a process kill as is; both are null while no rest is running. Added in schema v2.
 *
 * [mode] says whether the workout was logged live or recorded after the fact (FR-3.11), stored
 * by name. Added in schema v5; every earlier workout was live.
 */
@Entity(
    tableName = "workouts",
    foreignKeys = [
        // Deleting a routine must not delete the history that was started from it.
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["startedAt"], orders = [Index.Order.DESC]),
        Index("routineId"),
    ],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val routineId: String?,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val note: String?,
    val restStartedAt: Instant? = null,
    val restEndsAt: Instant? = null,
    @ColumnInfo(defaultValue = "LIVE") val mode: WorkoutMode = WorkoutMode.LIVE,
)
