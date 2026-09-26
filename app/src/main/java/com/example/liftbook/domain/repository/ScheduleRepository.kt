package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.ScheduleDraft
import com.example.liftbook.domain.model.WorkoutSchedule
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * The weekly workout schedule (FR-7.1). Nothing here sets an alarm: after a change, the caller
 * brings the reminders in step with `WorkoutReminders.sync` (FR-7.6).
 */
interface ScheduleRepository {

    /** Every entry, earliest in the day first, each with its routine's name. */
    fun observeSchedules(): Flow<List<WorkoutSchedule>>

    /** Emits null if there's no entry with this id — for example once it's deleted. */
    fun observeSchedule(id: String): Flow<WorkoutSchedule?>

    suspend fun getSchedules(): List<WorkoutSchedule>

    /** Adds an entry, turned on, and returns its id. [draft] must be valid. */
    suspend fun create(draft: ScheduleDraft): String

    /**
     * Replaces an entry's days, time, routine and lead time with [draft], and turns it on. A
     * snooze or a skip belonged to the old plan, so both are cleared.
     */
    suspend fun update(id: String, draft: ScheduleDraft)

    /** Turns one entry on or off (FR-7.7), clearing any snooze. */
    suspend fun setEnabled(id: String, enabled: Boolean)

    suspend fun delete(id: String)

    /** Brings the entry's reminder back at [until] (FR-7.4). */
    suspend fun snooze(id: String, until: Instant)

    suspend fun clearSnooze(id: String)

    /** Skips the entry's workout on [date] (FR-7.4): no more reminders for it, snoozed or not. */
    suspend fun skip(id: String, date: LocalDate)
}
