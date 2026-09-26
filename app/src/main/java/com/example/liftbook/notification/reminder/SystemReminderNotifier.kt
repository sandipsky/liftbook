package com.example.liftbook.notification.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.liftbook.R
import com.example.liftbook.domain.model.ReminderNotice
import com.example.liftbook.domain.repository.ReminderNotifier
import com.example.liftbook.notification.channel.NotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Clock
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The reminder before a scheduled workout (FR-7.2): what it is and when it starts, with Start
 * now, Snooze and Skip today (FR-7.4). Tapping it opens the app offering to start the workout
 * (FR-7.3). One per schedule entry, tagged with its id, so a snooze coming back replaces it.
 */
@Singleton
class SystemReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderNotifier {

    private val notifications = NotificationManagerCompat.from(context)

    override fun show(notice: ReminderNotice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val time = DateFormat.getTimeFormat(context).format(Date.from(notice.startsAt))
        val title = notice.routineName?.let { context.getString(R.string.reminder_notification_title_routine, it, time) }
            ?: context.getString(R.string.reminder_notification_title, time)
        val text = if (notice.minutesToStart > 0) {
            context.resources.getQuantityString(R.plurals.reminder_notification_starts_in, notice.minutesToStart, notice.minutesToStart)
        } else {
            context.getString(R.string.reminder_notification_starting)
        }
        val exercises = notice.exerciseNames.joinToString(context.getString(R.string.list_separator))
        val requestCode = notice.scheduleId.hashCode()

        val builder = NotificationCompat.Builder(context, NotificationChannels.WORKOUT_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(notice.startsAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(activity(ReminderIntents.open(context, notice, start = false), requestCode))
            .addAction(0, context.getString(R.string.reminder_action_start), activity(ReminderIntents.open(context, notice, start = true), requestCode))
            .addAction(
                0,
                context.getString(R.string.reminder_action_snooze, notice.snoozeMinutes),
                broadcast(ReminderIntents.action(context, notice, ReminderIntents.ACTION_SNOOZE), requestCode),
            )
            .addAction(
                0,
                context.getString(R.string.reminder_action_skip),
                broadcast(ReminderIntents.action(context, notice, ReminderIntents.ACTION_SKIP), requestCode),
            )
            .setAutoCancel(true)
        if (exercises.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.reminder_notification_expanded, text, exercises)))
        }
        // Gone an hour after the start, when it's no longer a reminder of anything.
        val lingers = Duration.between(clock.instant(), notice.startsAt.plus(LINGER))
        if (!lingers.isNegative) builder.setTimeoutAfter(lingers.toMillis())

        notifications.notify(notice.scheduleId, NOTIFICATION_ID, builder.build())
    }

    override fun dismiss(scheduleId: String) = notifications.cancel(scheduleId, NOTIFICATION_ID)

    private fun activity(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun broadcast(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private companion object {
        /** Tagged with the schedule entry's id; the rest timer's alert is 1 with no tag. */
        const val NOTIFICATION_ID = 2
        val LINGER: Duration = Duration.ofHours(1)
    }
}
