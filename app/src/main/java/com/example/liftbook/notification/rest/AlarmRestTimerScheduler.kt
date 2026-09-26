package com.example.liftbook.notification.rest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.notification.setWakeUpAlarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One exact alarm per rest (architecture §5.3): `setExactAndAllowWhileIdle` fires in Doze and
 * after a process kill, which a countdown in the app can't. A foreground service was the
 * alternative, rejected because Android 14's short-service type caps at three minutes.
 *
 * Exact alarms need the user's permission from Android 14 on. Without it the alarm is inexact —
 * it can arrive late — and the workout screen offers the setting (see AlertPermissions). Alarms
 * don't survive a reboot; BootReceiver sets the rest's again.
 */
@Singleton
class AlarmRestTimerScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RestTimerScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAt: Instant, nextExerciseName: String?) {
        // A new rest makes the last one's alert stale.
        RestTimerNotification.cancel(context)
        alarmManager?.setWakeUpAlarm(endsAt, alarmIntent(nextExerciseName))
    }

    override fun cancel() {
        alarmManager?.cancel(alarmIntent(nextExerciseName = null))
        RestTimerNotification.cancel(context)
    }

    /** The same request code and target every time, so each rest replaces the last (extras don't count). */
    private fun alarmIntent(nextExerciseName: String?): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java)
            .putExtra(RestTimerReceiver.EXTRA_NEXT_EXERCISE, nextExerciseName)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 1
    }
}
