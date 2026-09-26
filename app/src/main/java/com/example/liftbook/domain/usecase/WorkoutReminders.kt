package com.example.liftbook.domain.usecase

import com.example.liftbook.domain.calculator.Reminder
import com.example.liftbook.domain.calculator.ReminderTimes
import com.example.liftbook.domain.model.ReminderNotice
import com.example.liftbook.domain.repository.ReminderAlarm
import com.example.liftbook.domain.repository.ReminderNotifier
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Workout reminders (FR-7.2–7.7): which one is due, whether to send it, and when the next is.
 *
 * One alarm covers the whole schedule. [sync] is the single path every trigger takes — the alarm
 * going off, a boot, the clock or time zone changing, an edit to the schedule or its settings,
 * an import, the app opening (FR-7.6). It sends whatever came due since the alarm was set, then
 * sets the alarm for the next reminder. So an alarm that's due but hasn't gone off yet, as an
 * inexact one can be, is delivered rather than replaced, and nothing is ever sent twice.
 *
 * Whether to send is decided as the reminder goes out, not when it's scheduled (FR-7.5): not
 * while a workout is in progress, and not once one was finished earlier that day.
 */
class WorkoutReminders(
    private val schedules: ScheduleRepository,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    private val settings: SettingsRepository,
    private val alarm: ReminderAlarm,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    /** Syncs take turns, so the alarm firing and an edit landing together can't both send a reminder. */
    private val mutex = Mutex()

    suspend fun sync() = mutex.withLock { syncLocked() }

    /** Puts the entry's reminder off for the snooze length (FR-7.4). */
    suspend fun snooze(scheduleId: String) = mutex.withLock {
        notifier.dismiss(scheduleId)
        val minutes = settings.userPreferences.first().snoozeMinutes
        schedules.snooze(scheduleId, clock.instant().plus(Duration.ofMinutes(minutes.toLong())))
        syncLocked()
    }

    /** No more reminders for the entry's workout starting at [startsAt] (FR-7.4). */
    suspend fun skip(scheduleId: String, startsAt: Instant) = mutex.withLock {
        notifier.dismiss(scheduleId)
        schedules.skip(scheduleId, startsAt.atZone(clock.zone).toLocalDate())
        syncLocked()
    }

    /** Takes down the entry's reminder, once the app has answered it (FR-7.3). */
    fun dismiss(scheduleId: String) = notifier.dismiss(scheduleId)

    private suspend fun syncLocked() {
        val now = clock.instant()
        val preferences = settings.userPreferences.first()
        val all = schedules.getSchedules()
        val armedAt = alarm.armedAt()
        if (preferences.remindersEnabled && armedAt != null && !armedAt.isAfter(now)) {
            deliver(ReminderTimes.due(all, from = armedAt, until = now, clock.zone, preferences.reminderLeadMinutes), now, preferences.snoozeMinutes)
        }
        // Snoozes just delivered are in the past, so the next reminder can't be one of them.
        val next = if (preferences.remindersEnabled) {
            ReminderTimes.nextAfter(all, after = now, clock.zone, preferences.reminderLeadMinutes)
        } else {
            null
        }
        if (next != null) alarm.arm(next) else alarm.disarm()
    }

    private suspend fun deliver(due: List<Reminder>, now: Instant, snoozeMinutes: Int) {
        if (due.isEmpty()) return
        val training = workouts.hasActiveWorkout()
        val lastFinishedAt = workouts.lastFinishedAt()
        for (reminder in due) {
            if (reminder.isSnooze) schedules.clearSnooze(reminder.schedule.id)
            val dayStart = reminder.startsAt.atZone(clock.zone).toLocalDate().atStartOfDay(clock.zone).toInstant()
            val trainedThatDay = lastFinishedAt != null && !lastFinishedAt.isBefore(dayStart)
            if (training || trainedThatDay) continue

            val routine = reminder.schedule.routineId?.let { routines.getRoutine(it) }
            notifier.show(
                ReminderNotice(
                    scheduleId = reminder.schedule.id,
                    startsAt = reminder.startsAt,
                    minutesToStart = minutesBetween(now, reminder.startsAt),
                    routineId = routine?.id,
                    routineName = routine?.name,
                    exerciseNames = routine?.exercises?.map { it.exercise.name }.orEmpty(),
                    snoozeMinutes = snoozeMinutes,
                ),
            )
        }
    }

    /** Whole minutes from [now] to [then], rounded up, so a reminder 9 min 30 s ahead says 10. */
    private fun minutesBetween(now: Instant, then: Instant): Int {
        val seconds = Duration.between(now, then).seconds
        return if (seconds > 0) ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt() else 0
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
