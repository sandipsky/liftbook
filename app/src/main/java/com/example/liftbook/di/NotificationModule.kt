package com.example.liftbook.di

import com.example.liftbook.domain.repository.ReminderAlarm
import com.example.liftbook.domain.repository.ReminderNotifier
import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.domain.usecase.WorkoutReminders
import com.example.liftbook.notification.reminder.AlarmReminderAlarm
import com.example.liftbook.notification.reminder.SystemReminderNotifier
import com.example.liftbook.notification.rest.AlarmRestTimerScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    abstract fun bindRestTimerScheduler(impl: AlarmRestTimerScheduler): RestTimerScheduler

    @Binds
    abstract fun bindReminderAlarm(impl: AlarmReminderAlarm): ReminderAlarm

    @Binds
    abstract fun bindReminderNotifier(impl: SystemReminderNotifier): ReminderNotifier

    companion object {
        /** One instance, so its lock covers every sync — the alarm's, a boot's and the app's. */
        @Provides
        @Singleton
        fun provideWorkoutReminders(
            schedules: ScheduleRepository,
            routines: RoutineRepository,
            workouts: WorkoutRepository,
            settings: SettingsRepository,
            alarm: ReminderAlarm,
            notifier: ReminderNotifier,
            clock: Clock,
        ): WorkoutReminders = WorkoutReminders(schedules, routines, workouts, settings, alarm, notifier, clock)
    }
}
