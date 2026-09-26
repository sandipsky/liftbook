package com.example.liftbook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * A scheduled workout (FR-7.1): the days it's on and when it starts, as local time. Added in
 * schema v4.
 *
 * One entry covers several days, as an alarm clock's does, so "Mon, Wed, Fri at 18:00" is one
 * row: [daysOfWeek] is a set of ISO days as bits, Monday the lowest — 1 is Monday, 2 Tuesday,
 * up to 64 for Sunday. Never 0. [snoozedUntil] and [skippedOn] are the state of today's
 * reminder (FR-7.4), not part of the plan: editing the entry clears them, and backups leave
 * them out.
 */
@Entity(
    tableName = "workout_schedules",
    foreignKeys = [
        // Deleting a routine keeps the schedule; its reminders just stop naming one.
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId")],
)
data class WorkoutScheduleEntity(
    @PrimaryKey val id: String,
    val daysOfWeek: Int,
    /** Minutes from midnight. */
    val startTimeMinutes: Int,
    val routineId: String?,
    /** Null follows the global default (FR-7.2). */
    val leadTimeMinutes: Int?,
    val isEnabled: Boolean,
    val snoozedUntil: Instant?,
    val skippedOn: LocalDate?,
    val createdAt: Instant,
)
