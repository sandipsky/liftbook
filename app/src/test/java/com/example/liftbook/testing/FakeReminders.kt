package com.example.liftbook.testing

import com.example.liftbook.domain.model.ReminderNotice
import com.example.liftbook.domain.repository.ReminderAlarm
import com.example.liftbook.domain.repository.ReminderNotifier
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.domain.usecase.WorkoutReminders
import java.time.Clock
import java.time.Instant

/** Records the reminder alarm instead of setting one. */
class FakeReminderAlarm : ReminderAlarm {

    /** When the alarm would go off; null when none is set. */
    var at: Instant? = null

    override suspend fun armedAt(): Instant? = at

    override suspend fun arm(at: Instant) {
        this.at = at
    }

    override suspend fun disarm() {
        at = null
    }
}

/** Records reminders instead of posting them. */
class FakeReminderNotifier : ReminderNotifier {

    /** What's showing, by schedule entry. */
    val showing = linkedMapOf<String, ReminderNotice>()

    /** Everything ever shown, in order. */
    val shown = mutableListOf<ReminderNotice>()

    override fun show(notice: ReminderNotice) {
        showing[notice.scheduleId] = notice
        shown += notice
    }

    override fun dismiss(scheduleId: String) {
        showing.remove(scheduleId)
    }
}

/** The real reminder logic, over fakes. */
fun testReminders(
    schedules: ScheduleRepository = FakeScheduleRepository(),
    routines: RoutineRepository = FakeRoutineRepository(FakeExerciseRepository()),
    workouts: WorkoutRepository = FakeWorkoutRepository(FakeRoutineRepository(FakeExerciseRepository())),
    settings: SettingsRepository = FakeSettingsRepository(),
    alarm: FakeReminderAlarm = FakeReminderAlarm(),
    notifier: FakeReminderNotifier = FakeReminderNotifier(),
    clock: Clock,
): WorkoutReminders = WorkoutReminders(schedules, routines, workouts, settings, alarm, notifier, clock)
