package com.example.liftbook.ui.components

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Why an alert on a timer — the rest timer's (FR-3.5), a workout reminder (FR-7.2) — might not
 * reach the user with the screen off, or on time.
 */
enum class AlertIssue {
    /** Notifications — or the alert's own channel — are turned off, or not yet allowed (Android 13+). */
    NotificationsOff,

    /** Exact alarms aren't allowed (Android 12+), so the alert can arrive late. */
    AlarmsOff,
}

/**
 * What stands between the alerts on [channelId] and the user right now, rechecked each time the
 * screen resumes — the fix is made in system settings, and the user comes back from there.
 */
@Composable
fun rememberAlertIssue(channelId: String): AlertIssueState {
    val context = LocalContext.current
    val state = remember(channelId) { AlertIssueState(context.applicationContext, channelId) }
    LifecycleResumeEffect(state) {
        state.recheck()
        onPauseOrDispose {}
    }
    return state
}

class AlertIssueState internal constructor(private val context: Context, private val channelId: String) {
    var issue by mutableStateOf(alertIssue(context, channelId))
        private set

    fun recheck() {
        issue = alertIssue(context, channelId)
    }
}

fun alertIssue(context: Context, channelId: String): AlertIssue? {
    val notifications = NotificationManagerCompat.from(context)
    val channelOff = notifications.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
    if (!notifications.areNotificationsEnabled() || channelOff) return AlertIssue.NotificationsOff
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms != null && !alarms.canScheduleExactAlarms()) return AlertIssue.AlarmsOff
    }
    return null
}

/** Opens the system setting that fixes [issue]. */
fun openAlertSettings(context: Context, issue: AlertIssue) {
    val intent = when (issue) {
        AlertIssue.NotificationsOff -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        AlertIssue.AlarmsOff -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
        } else {
            return
        }
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Some builds lack the screen; the app's own settings page always exists.
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}
