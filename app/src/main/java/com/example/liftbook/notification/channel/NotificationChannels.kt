package com.example.liftbook.notification.channel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.liftbook.R

/**
 * The app's notification channels. Created at startup; creating an existing channel only
 * updates its name and description, so the user's own settings for it are kept.
 */
object NotificationChannels {

    /** Rest over (FR-3.5). High importance, so it buzzes and shows with the screen off. */
    const val REST_TIMER = "rest_timer"

    /** A scheduled workout is coming up (FR-7.2). High importance: it's for a set time, like an alarm. */
    const val WORKOUT_REMINDERS = "workout_reminders"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val restTimer = NotificationChannel(
            REST_TIMER,
            context.getString(R.string.channel_rest_timer),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_rest_timer_description)
            enableVibration(true)
            vibrationPattern = REST_VIBRATION
            setShowBadge(false)
        }
        val reminders = NotificationChannel(
            WORKOUT_REMINDERS,
            context.getString(R.string.channel_workout_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_workout_reminders_description)
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(restTimer, reminders))
    }

    /** Two firm pulses: distinct from a message, easy to feel through a pocket or on a bench. */
    private val REST_VIBRATION = longArrayOf(0, 400, 200, 400)
}
