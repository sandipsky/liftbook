package com.example.liftbook.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A scheduled workout (FR-7.1): the days of the week it's on, when it starts, and optionally the
 * routine it's for. LiftBook reminds the user a lead time before each one (FR-7.2).
 */
data class WorkoutSchedule(
    val id: String,
    /** Never empty. */
    val days: Set<DayOfWeek>,
    /** Local time, on each of [days]. */
    val startTime: LocalTime,
    val routineId: String? = null,
    /** The linked routine's name, for showing; null without one. */
    val routineName: String? = null,
    /** How long before the start to remind; null follows the global default (FR-7.2). */
    val leadMinutes: Int? = null,
    /** Per-entry on/off (FR-7.7); the global switch is a setting. */
    val isEnabled: Boolean = true,
    /** When a snoozed reminder comes back (FR-7.4); null when none is snoozed. */
    val snoozedUntil: Instant? = null,
    /** The day whose workout was skipped from its reminder (FR-7.4): it isn't reminded again. */
    val skippedOn: LocalDate? = null,
) {
    fun leadMinutesOr(defaultMinutes: Int): Int = leadMinutes ?: defaultMinutes
}

/** What the reminder editor saves (FR-7.1, FR-7.2). Saving turns the entry on, as setting an alarm does. */
data class ScheduleDraft(
    val days: Set<DayOfWeek>,
    val startTime: LocalTime,
    val routineId: String? = null,
    /** Null follows the global default. */
    val leadMinutes: Int? = null,
) {
    val isValid: Boolean get() = days.isNotEmpty() && (leadMinutes == null || leadMinutes in 0..MAX_LEAD_MINUTES)

    companion object {
        /** A day: anything longer is a reminder for a different workout. */
        const val MAX_LEAD_MINUTES = 24 * 60
    }
}

/** A reminder to show now (FR-7.2): what's starting, and when. */
data class ReminderNotice(
    val scheduleId: String,
    val startsAt: Instant,
    /** Minutes from now until [startsAt], rounded up; 0 or less once it has started. */
    val minutesToStart: Int,
    val routineId: String?,
    val routineName: String?,
    /** The routine's exercises, in order, for the notification's expanded text. */
    val exerciseNames: List<String>,
    /** How long Snooze puts it off, to name on the action (FR-7.4). */
    val snoozeMinutes: Int,
)

/** A reminder that opened the app (FR-7.3, FR-7.4). */
data class ReminderLaunch(
    val scheduleId: String,
    val routineId: String?,
    val startsAt: Instant,
    /** Start now (FR-7.4), rather than open the app and offer to (FR-7.3). */
    val startNow: Boolean,
)
