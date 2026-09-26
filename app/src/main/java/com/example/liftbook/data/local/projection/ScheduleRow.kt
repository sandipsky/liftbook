package com.example.liftbook.data.local.projection

import androidx.room.Embedded
import com.example.liftbook.data.local.entity.WorkoutScheduleEntity

/** A schedule entry with its routine's name, for the list and the reminder's text (FR-7.1). */
data class ScheduleRow(
    @Embedded val schedule: WorkoutScheduleEntity,
    val routineName: String?,
)
