package com.example.liftbook.notification.rest

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.liftbook.MainActivity
import com.example.liftbook.R
import com.example.liftbook.notification.channel.NotificationChannels

/** The "rest over" notification (FR-3.5). */
internal object RestTimerNotification {

    private const val NOTIFICATION_ID = 1

    /** How long an unread alert stays up: long enough to see, short enough not to linger. */
    private const val TIMEOUT_MILLIS = 10 * 60 * 1_000L

    fun show(context: Context, nextExerciseName: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val text = nextExerciseName?.let { context.getString(R.string.rest_notification_next, it) }
            ?: context.getString(R.string.rest_notification_text)
        val notification = NotificationCompat.Builder(context, NotificationChannels.REST_TIMER)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(context.getString(R.string.rest_notification_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MILLIS)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    /**
     * Brings the app back as the user left it — the workout is where they were. If the app was
     * killed, it opens on Home, where the banner keeps the workout one tap away (FR-3.7).
     */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
