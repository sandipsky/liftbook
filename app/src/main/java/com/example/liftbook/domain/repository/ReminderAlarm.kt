package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.ReminderNotice
import java.time.Instant

/**
 * The one alarm behind every workout reminder (FR-7.6), set for the next reminder due. It fires
 * in Doze and after the app is killed; without the exact-alarm permission it may be late.
 */
interface ReminderAlarm {

    /** When the alarm that's set goes off; null when none is. Kept until the alarm is replaced or cleared. */
    suspend fun armedAt(): Instant?

    /** Replaces any alarm with one at [at]. */
    suspend fun arm(at: Instant)

    suspend fun disarm()
}

/** Shows and takes down workout reminders (FR-7.2–7.4). One per schedule entry at a time. */
interface ReminderNotifier {

    /** Shows [notice], replacing any reminder already showing for its entry. */
    fun show(notice: ReminderNotice)

    fun dismiss(scheduleId: String)
}
