package com.example.liftbook.testing

import com.example.liftbook.domain.model.ScheduleDraft
import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate

/**
 * An in-memory [ScheduleRepository] with the real rules: entries earliest in the day first, and
 * saving one turns it on and clears its snooze and skip. Ids are "schedule-1", "schedule-2"….
 * Routine names come from [routineNames], as Room's join would give them.
 */
class FakeScheduleRepository(initial: List<WorkoutSchedule> = emptyList()) : ScheduleRepository {

    val schedules = MutableStateFlow(initial)

    /** Routine names by id, for the entries that link one. */
    var routineNames: Map<String, String> = emptyMap()

    /** When set, the next write throws, as a failing database would. */
    var failNextWrite = false

    private var nextId = 1

    override fun observeSchedules(): Flow<List<WorkoutSchedule>> = schedules.map { all -> all.sorted() }

    override fun observeSchedule(id: String): Flow<WorkoutSchedule?> = schedules.map { all -> all.firstOrNull { it.id == id }?.named() }

    override suspend fun getSchedules(): List<WorkoutSchedule> = schedules.value.sorted()

    override suspend fun create(draft: ScheduleDraft): String {
        failIfAsked()
        require(draft.isValid)
        val id = "schedule-${nextId++}"
        schedules.update { it + WorkoutSchedule(id, draft.days, draft.startTime, draft.routineId, leadMinutes = draft.leadMinutes) }
        return id
    }

    override suspend fun update(id: String, draft: ScheduleDraft) {
        failIfAsked()
        require(draft.isValid)
        edit(id) {
            it.copy(
                days = draft.days,
                startTime = draft.startTime,
                routineId = draft.routineId,
                leadMinutes = draft.leadMinutes,
                isEnabled = true,
                snoozedUntil = null,
                skippedOn = null,
            )
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        failIfAsked()
        edit(id) { it.copy(isEnabled = enabled, snoozedUntil = null) }
    }

    override suspend fun delete(id: String) {
        failIfAsked()
        schedules.update { all -> all.filterNot { it.id == id } }
    }

    override suspend fun snooze(id: String, until: Instant) = edit(id) { it.copy(snoozedUntil = until) }

    override suspend fun clearSnooze(id: String) = edit(id) { it.copy(snoozedUntil = null) }

    override suspend fun skip(id: String, date: LocalDate) = edit(id) { it.copy(skippedOn = date, snoozedUntil = null) }

    fun get(id: String): WorkoutSchedule? = schedules.value.firstOrNull { it.id == id }

    private fun edit(id: String, change: (WorkoutSchedule) -> WorkoutSchedule) =
        schedules.update { all -> all.map { if (it.id == id) change(it) else it } }

    private fun List<WorkoutSchedule>.sorted(): List<WorkoutSchedule> = sortedBy { it.startTime }.map { it.named() }

    private fun WorkoutSchedule.named(): WorkoutSchedule = copy(routineName = routineId?.let(routineNames::get))

    private fun failIfAsked() {
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("Write failed")
        }
    }
}
