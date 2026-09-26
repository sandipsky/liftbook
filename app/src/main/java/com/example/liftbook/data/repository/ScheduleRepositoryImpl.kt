package com.example.liftbook.data.repository

import com.example.liftbook.data.local.dao.ScheduleDao
import com.example.liftbook.data.mapper.minutesOfDay
import com.example.liftbook.data.mapper.toBits
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.data.mapper.toEntity
import com.example.liftbook.domain.model.ScheduleDraft
import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val clock: Clock,
) : ScheduleRepository {

    override fun observeSchedules(): Flow<List<WorkoutSchedule>> =
        scheduleDao.observeAll().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeSchedule(id: String): Flow<WorkoutSchedule?> =
        scheduleDao.observeById(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getSchedules(): List<WorkoutSchedule> = scheduleDao.getAll().map { it.toDomain() }

    override suspend fun create(draft: ScheduleDraft): String {
        require(draft.isValid) { "A schedule entry needs a day, and a lead time within a day" }
        val id = UUID.randomUUID().toString()
        scheduleDao.insert(draft.toEntity(id, createdAt = clock.instant()))
        return id
    }

    override suspend fun update(id: String, draft: ScheduleDraft) {
        require(draft.isValid) { "A schedule entry needs a day, and a lead time within a day" }
        val existing = scheduleDao.getById(id) ?: return
        scheduleDao.update(
            existing.copy(
                daysOfWeek = draft.days.toBits(),
                startTimeMinutes = draft.startTime.minutesOfDay(),
                routineId = draft.routineId,
                leadTimeMinutes = draft.leadMinutes,
                isEnabled = true,
                snoozedUntil = null,
                skippedOn = null,
            ),
        )
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = scheduleDao.setEnabled(id, enabled)

    override suspend fun delete(id: String) = scheduleDao.delete(id)

    override suspend fun snooze(id: String, until: Instant) = scheduleDao.setSnoozedUntil(id, until)

    override suspend fun clearSnooze(id: String) = scheduleDao.setSnoozedUntil(id, null)

    override suspend fun skip(id: String, date: LocalDate) = scheduleDao.skip(id, date)
}
