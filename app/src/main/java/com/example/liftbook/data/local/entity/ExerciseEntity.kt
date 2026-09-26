package com.example.liftbook.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import java.time.Instant

/**
 * Library queries filter on `isArchived = 0`; history queries never do. That asymmetry is what
 * makes archiving safe: an archived exercise vanishes from the library while every set logged
 * against it stays readable (FR-1.3).
 */
@Entity(
    tableName = "exercises",
    indices = [Index("name"), Index("primaryMuscle"), Index("equipment")],
)
data class ExerciseEntity(
    /** Built-in exercises carry hardcoded ids (see ExerciseSeed) so exports resolve across devices. */
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val type: ExerciseType,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val defaultRestSeconds: Int?,
    val notes: String?,
    val createdAt: Instant,
)
