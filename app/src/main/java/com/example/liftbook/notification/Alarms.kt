package com.example.liftbook.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import java.time.Instant

/**
 * Sets an alarm that wakes the phone at [at], in Doze too (NFR-3, FR-7.6): exact when the app
 * may set exact alarms — always before Android 12, and from Android 14 only once the user allows
 * it — and otherwise as close as the system allows, which can be minutes late.
 */
internal fun AlarmManager.setWakeUpAlarm(at: Instant, operation: PendingIntent) {
    val millis = at.toEpochMilli()
    try {
        if (canSetExactAlarms()) {
            setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        } else {
            setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    } catch (e: SecurityException) {
        // Revoked between the check and the call.
        setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
    }
}

internal fun AlarmManager.canSetExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
