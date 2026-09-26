package com.example.liftbook.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A weigh-in (FR-5.4). One a day: [recordedOn] is unique, so logging a day again replaces it.
 * Added in schema v3. [note] isn't edited yet; it's in the table so an export can carry one
 * without a migration.
 */
@Entity(
    tableName = "body_weight_entries",
    indices = [Index(value = ["recordedOn"], unique = true)],
)
data class BodyWeightEntryEntity(
    @PrimaryKey val id: String,
    /** Kilograms — the only unit ever stored (FR-6.1). */
    val weightKg: Double,
    val recordedOn: LocalDate,
    val note: String?,
)
