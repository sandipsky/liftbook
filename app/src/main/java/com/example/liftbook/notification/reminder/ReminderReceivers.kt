package com.example.liftbook.notification.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.liftbook.domain.calculator.nextSetAfter
import com.example.liftbook.notification.receiverEntryPoint
import com.example.liftbook.notification.runAsync
import kotlinx.coroutines.flow.first
import java.time.Instant

/** The reminder alarm went off: send what's due and set the next (FR-7.2, FR-7.5). */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminders = context.receiverEntryPoint().workoutReminders()
        runAsync { reminders.sync() }
    }
}

/** Snooze and Skip today, answered from the notification without opening the app (FR-7.4). */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(ReminderIntents.EXTRA_SCHEDULE_ID) ?: return
        val reminders = context.receiverEntryPoint().workoutReminders()
        when (intent.action) {
            ReminderIntents.ACTION_SNOOZE -> runAsync { reminders.snooze(scheduleId) }
            ReminderIntents.ACTION_SKIP -> {
                val startsAt = intent.getLongExtra(ReminderIntents.EXTRA_STARTS_AT, -1L).takeIf { it >= 0 } ?: return
                runAsync { reminders.skip(scheduleId, Instant.ofEpochMilli(startsAt)) }
            }
        }
    }
}

/**
 * Alarms are lost when the phone restarts or the app is updated, and a schedule's local times
 * move when the clock or the time zone does. Each of those sets the reminder alarm again
 * (FR-7.6), and the rest timer's if a rest is still running (NFR-3). So does the user allowing
 * exact alarms, which turns an inexact alarm exact.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val graph = context.receiverEntryPoint()
        runAsync {
            graph.workoutReminders().sync()
            val workout = graph.workoutRepository().observeActiveWorkout().first() ?: return@runAsync
            val rest = workout.rest ?: return@runAsync
            if (rest.endsAt.isAfter(graph.clock().instant())) {
                // The set whose completion started the rest shares its instant (architecture §2.4).
                val restedAfter = workout.exercises.flatMap { it.sets }.firstOrNull { it.completedAt == rest.startedAt }
                val next = restedAfter?.let { workout.nextSetAfter(it.id) }
                graph.restTimerScheduler().schedule(rest.endsAt, next?.exercise?.exercise?.name)
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, from Android 12.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
