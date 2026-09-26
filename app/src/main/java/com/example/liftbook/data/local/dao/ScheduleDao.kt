package com.example.liftbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.liftbook.data.local.entity.WorkoutScheduleEntity
import com.example.liftbook.data.local.projection.ScheduleRow
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface ScheduleDao {

    /** Earliest in the day first; entries at the same time in the order they were made. */
    @Query("$SCHEDULE_ROWS ORDER BY s.startTimeMinutes, s.createdAt, s.id")
    fun observeAll(): Flow<List<ScheduleRow>>

    @Query("$SCHEDULE_ROWS ORDER BY s.startTimeMinutes, s.createdAt, s.id")
    suspend fun getAll(): List<ScheduleRow>

    @Query("$SCHEDULE_ROWS WHERE s.id = :id")
    fun observeById(id: String): Flow<ScheduleRow?>

    @Query("SELECT * FROM workout_schedules WHERE id = :id")
    suspend fun getById(id: String): WorkoutScheduleEntity?

    @Insert
    suspend fun insert(schedule: WorkoutScheduleEntity)

    @Update
    suspend fun update(schedule: WorkoutScheduleEntity)

    @Query("UPDATE workout_schedules SET isEnabled = :enabled, snoozedUntil = NULL WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE workout_schedules SET snoozedUntil = :until WHERE id = :id")
    suspend fun setSnoozedUntil(id: String, until: Instant?)

    @Query("UPDATE workout_schedules SET skippedOn = :date, snoozedUntil = NULL WHERE id = :id")
    suspend fun skip(id: String, date: LocalDate)

    @Query("DELETE FROM workout_schedules WHERE id = :id")
    suspend fun delete(id: String)
}

private const val SCHEDULE_ROWS = """
    SELECT s.*, r.name AS routineName
    FROM workout_schedules AS s
    LEFT JOIN routines AS r ON r.id = s.routineId
"""
