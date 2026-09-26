package com.example.liftbook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A routine template (FR-2.1). Part of schema v1 because workouts reference it; the routines
 * feature itself arrives with FR-2.x. Last-performed date is derived, never stored (FR-2.4).
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
