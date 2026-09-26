package com.example.liftbook.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.domain.model.ScheduleDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The workout schedule (FR-7.1) against a real, in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class ScheduleRepositoryTest {

    private lateinit var database: LiftBookDatabase
    private lateinit var repository: ScheduleRepositoryImpl

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepositoryImpl(database.scheduleDao(), Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC))
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addRoutine(id: String, name: String) =
        database.routineDao().insert(RoutineEntity(id, name, notes = null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))

    @Test
    fun `an entry comes back as saved, on, with its routine's name`() = runTest {
        addRoutine("push", "Push")

        val id = repository.create(ScheduleDraft(weekdays, LocalTime.of(18, 30), routineId = "push", leadMinutes = 30))

        val schedule = repository.observeSchedule(id).first()!!
        assertEquals(weekdays, schedule.days)
        assertEquals(LocalTime.of(18, 30), schedule.startTime)
        assertEquals("Push", schedule.routineName)
        assertEquals(30, schedule.leadMinutes)
        assertTrue(schedule.isEnabled)
    }

    @Test
    fun `entries come earliest in the day first`() = runTest {
        repository.create(ScheduleDraft(setOf(DayOfWeek.SUNDAY), LocalTime.of(18, 0)))
        repository.create(ScheduleDraft(setOf(DayOfWeek.MONDAY), LocalTime.of(6, 45)))

        assertEquals(listOf(LocalTime.of(6, 45), LocalTime.of(18, 0)), repository.getSchedules().map { it.startTime })
    }

    @Test
    fun `saving an edit replaces the plan, turns it on, and clears today's snooze and skip`() = runTest {
        val id = repository.create(ScheduleDraft(weekdays, LocalTime.of(18, 0)))
        repository.snooze(id, Instant.parse("2026-09-26T18:10:00Z"))
        repository.skip(id, LocalDate.of(2026, 9, 26))
        repository.setEnabled(id, enabled = false)

        repository.update(id, ScheduleDraft(setOf(DayOfWeek.SATURDAY), LocalTime.of(9, 0), leadMinutes = 0))

        val schedule = repository.getSchedules().single()
        assertEquals(setOf(DayOfWeek.SATURDAY), schedule.days)
        assertEquals(LocalTime.of(9, 0), schedule.startTime)
        assertEquals(0, schedule.leadMinutes)
        assertTrue(schedule.isEnabled)
        assertNull(schedule.snoozedUntil)
        assertNull(schedule.skippedOn)
    }

    @Test
    fun `a snooze and a skip are kept, and a skip ends the snooze`() = runTest {
        val id = repository.create(ScheduleDraft(weekdays, LocalTime.of(18, 0)))

        repository.snooze(id, Instant.parse("2026-09-26T18:10:00Z"))
        assertEquals(Instant.parse("2026-09-26T18:10:00Z"), repository.getSchedules().single().snoozedUntil)

        repository.skip(id, LocalDate.of(2026, 9, 26))
        val skipped = repository.getSchedules().single()
        assertEquals(LocalDate.of(2026, 9, 26), skipped.skippedOn)
        assertNull(skipped.snoozedUntil)
    }

    @Test
    fun `switching an entry off clears its snooze`() = runTest {
        val id = repository.create(ScheduleDraft(weekdays, LocalTime.of(18, 0)))
        repository.snooze(id, Instant.parse("2026-09-26T18:10:00Z"))

        repository.setEnabled(id, enabled = false)

        val schedule = repository.getSchedules().single()
        assertFalse(schedule.isEnabled)
        assertNull(schedule.snoozedUntil)
    }

    @Test
    fun `deleting its routine keeps the entry, unlinked`() = runTest {
        addRoutine("push", "Push")
        val id = repository.create(ScheduleDraft(weekdays, LocalTime.of(18, 0), routineId = "push"))

        database.routineDao().delete("push")

        val schedule = repository.observeSchedule(id).first()!!
        assertNull(schedule.routineId)
        assertNull(schedule.routineName)
    }

    @Test
    fun `an entry can be deleted`() = runTest {
        val id = repository.create(ScheduleDraft(weekdays, LocalTime.of(18, 0)))

        repository.delete(id)

        assertNull(repository.observeSchedule(id).first())
    }

    @Test
    fun `an entry needs a day`() = runTest {
        val failure = runCatching { repository.create(ScheduleDraft(emptySet(), LocalTime.of(18, 0))) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(repository.getSchedules().isEmpty())
    }
}
