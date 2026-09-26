package com.example.liftbook.ui.feature.workout

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
import com.example.liftbook.notification.channel.NotificationChannels

/** Why a rest alert might not reach the user with the screen off (FR-3.5). */
enum class RestAlertIssue {
    /** Notifications — or the rest timer's channel — are turned off, or not yet allowed (Android 13+). */
    NotificationsOff,

    /** Exact alarms aren't allowed (Android 12+), so the alert can arrive late. */
    AlarmsOff,
}

/**
 * What stands between the rest alert and the user right now, rechecked each time the screen
 * resumes — the fix is made in system settings, and the user comes back from there.
 */
@Composable
fun rememberRestAlertIssue(): RestAlertIssueState {
    val context = LocalContext.current
    val state = remember { RestAlertIssueState(context.applicationContext) }
    LifecycleResumeEffect(state) {
        state.recheck()
        onPauseOrDispose {}
    }
    return state
}

class RestAlertIssueState internal constructor(private val context: Context) {
    var issue by mutableStateOf(restAlertIssue(context))
        private set

    fun recheck() {
        issue = restAlertIssue(context)
    }
}

fun restAlertIssue(context: Context): RestAlertIssue? {
    val notifications = NotificationManagerCompat.from(context)
    val channelOff = notifications.getNotificationChannel(NotificationChannels.REST_TIMER)?.importance ==
        NotificationManager.IMPORTANCE_NONE
    if (!notifications.areNotificationsEnabled() || channelOff) return RestAlertIssue.NotificationsOff
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms != null && !alarms.canScheduleExactAlarms()) return RestAlertIssue.AlarmsOff
    }
    return null
}

/** Opens the system setting that fixes [issue]. */
fun openRestAlertSettings(context: Context, issue: RestAlertIssue) {
    val intent = when (issue) {
        RestAlertIssue.NotificationsOff -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        RestAlertIssue.AlarmsOff -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
