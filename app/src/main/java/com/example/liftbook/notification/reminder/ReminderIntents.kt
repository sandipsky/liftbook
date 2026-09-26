package com.example.liftbook.notification.reminder

import android.content.Context
import android.content.Intent
import com.example.liftbook.MainActivity
import com.example.liftbook.domain.model.ReminderLaunch
import com.example.liftbook.domain.model.ReminderNotice
import java.time.Instant

/**
 * What a reminder's taps carry into the app (FR-7.3, FR-7.4): which entry, which workout, and
 * whether to start it. Extras rather than a deep link, so the navigation graph never acts on the
 * intent by itself; MainActivity reads it and hands it to the app.
 */
object ReminderIntents {

    const val ACTION_OPEN = "com.example.liftbook.action.OPEN_REMINDER"
    const val ACTION_START = "com.example.liftbook.action.START_REMINDER"
    const val ACTION_SNOOZE = "com.example.liftbook.action.SNOOZE_REMINDER"
    const val ACTION_SKIP = "com.example.liftbook.action.SKIP_REMINDER"

    const val EXTRA_SCHEDULE_ID = "com.example.liftbook.extra.SCHEDULE_ID"
    const val EXTRA_ROUTINE_ID = "com.example.liftbook.extra.ROUTINE_ID"
    const val EXTRA_STARTS_AT = "com.example.liftbook.extra.STARTS_AT"

    /**
     * Opens the app on [notice]'s workout, offering to start it — or, with [start], starting it.
     * The app already running gets it in onNewIntent, as it was left, rather than a second copy.
     */
    fun open(context: Context, notice: ReminderNotice, start: Boolean): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(if (start) ACTION_START else ACTION_OPEN)
            .putExtras(notice)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Snooze or Skip today, answered without opening the app (FR-7.4). */
    fun action(context: Context, notice: ReminderNotice, action: String): Intent =
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtras(notice)

    /** The reminder that opened the app, if one did. */
    fun parse(intent: Intent): ReminderLaunch? {
        val start = when (intent.action) {
            ACTION_OPEN -> false
            ACTION_START -> true
            else -> return null
        }
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return null
        val startsAt = intent.getLongExtra(EXTRA_STARTS_AT, -1L).takeIf { it >= 0 } ?: return null
        return ReminderLaunch(
            scheduleId = scheduleId,
            routineId = intent.getStringExtra(EXTRA_ROUTINE_ID),
            startsAt = Instant.ofEpochMilli(startsAt),
            startNow = start,
        )
    }

    private fun Intent.putExtras(notice: ReminderNotice): Intent = this
        .putExtra(EXTRA_SCHEDULE_ID, notice.scheduleId)
        .putExtra(EXTRA_ROUTINE_ID, notice.routineId)
        .putExtra(EXTRA_STARTS_AT, notice.startsAt.toEpochMilli())
}
