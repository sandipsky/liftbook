package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.WorkoutSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One reminder: for [schedule]'s workout starting at [startsAt], due at [remindAt]. */
data class Reminder(
    val schedule: WorkoutSchedule,
    val startsAt: Instant,
    val remindAt: Instant,
    /** A snoozed reminder coming back (FR-7.4), rather than the regular one. */
    val isSnooze: Boolean = false,
)

/**
 * When workout reminders are due (FR-7.2, FR-7.4). A schedule's times are local: 18:00 is 18:00
 * wherever the phone is, and across a daylight-saving change — a start that falls in the hour
 * the clocks skip moves to just after it. Only enabled entries remind; the global switch is the
 * caller's to check.
 */
object ReminderTimes {

    /** The lead times offered, in minutes: at the start, up to two hours before. */
    val LEAD_OPTIONS = listOf(0, 5, 10, 15, 30, 45, 60, 90, 120)

    /** The snooze lengths offered, in minutes (FR-7.4). */
    val SNOOZE_OPTIONS = listOf(5, 10, 15, 30)

    /**
     * A reminder this long after its workout started — the phone was off, say — is no use and
     * isn't sent. Snoozes are exempt: the user asked for those.
     */
    val LATE_LIMIT: Duration = Duration.ofHours(1)

    /** Far enough ahead to reach any entry's next day, even past a skipped one. */
    private const val SEARCH_DAYS = 15L

    /**
     * The reminders due from [from] to [until], both included, in time order: one per entry at
     * most — its latest — since a newer reminder for the same entry replaces an older one.
     * Skipped days are left out, and so are reminders past [LATE_LIMIT].
     */
    fun due(
        schedules: List<WorkoutSchedule>,
        from: Instant,
        until: Instant,
        zone: ZoneId,
        defaultLeadMinutes: Int,
    ): List<Reminder> = schedules
        .filter { it.isEnabled }
        .mapNotNull { schedule ->
            val lead = schedule.lead(defaultLeadMinutes)
            val regular = schedule.remindersBetween(from, until, zone, lead)
                .filter { until.isBefore(it.startsAt.plus(LATE_LIMIT)) }
                .lastOrNull()
            val snooze = schedule.snoozedUntil
                ?.takeIf { !it.isBefore(from) && !it.isAfter(until) }
                ?.let { at -> Reminder(schedule, startsAt = schedule.startSnoozedAt(at, zone, lead), remindAt = at, isSnooze = true) }
            listOfNotNull(regular, snooze).maxByOrNull { it.remindAt }
        }
        .sortedBy { it.remindAt }

    /** When the first reminder after [after] is due — a regular one or a snooze — or null if none is. */
    fun nextAfter(schedules: List<WorkoutSchedule>, after: Instant, zone: ZoneId, defaultLeadMinutes: Int): Instant? = schedules
        .filter { it.isEnabled }
        .mapNotNull { schedule ->
            val lead = schedule.lead(defaultLeadMinutes)
            val regular = schedule.remindersBetween(after, after.plus(Duration.ofDays(SEARCH_DAYS)), zone, lead)
                .firstOrNull { it.remindAt.isAfter(after) }
                ?.remindAt
            val snooze = schedule.snoozedUntil?.takeIf { it.isAfter(after) }
            listOfNotNull(regular, snooze).minOrNull()
        }
        .minOrNull()

    /** The next scheduled workout to start after [after], with its entry: what the reminders screen leads with. */
    fun nextStart(schedules: List<WorkoutSchedule>, after: Instant, zone: ZoneId): Pair<WorkoutSchedule, Instant>? = schedules
        .filter { it.isEnabled }
        .mapNotNull { schedule ->
            schedule.remindersBetween(after, after.plus(Duration.ofDays(SEARCH_DAYS)), zone, lead = Duration.ZERO)
                .firstOrNull { it.startsAt.isAfter(after) }
                ?.let { schedule to it.startsAt }
        }
        .minByOrNull { it.second }

    /** How many workouts a week the enabled entries add up to. */
    fun perWeek(schedules: List<WorkoutSchedule>): Int = schedules.filter { it.isEnabled }.sumOf { it.days.size }

    private fun WorkoutSchedule.lead(defaultLeadMinutes: Int): Duration = Duration.ofMinutes(leadMinutesOr(defaultLeadMinutes).toLong())

    private fun WorkoutSchedule.startOn(date: LocalDate, zone: ZoneId): Instant = date.atTime(startTime).atZone(zone).toInstant()

    /** Regular reminders due from [from] to [until], both included, oldest first, skipped days left out. */
    private fun WorkoutSchedule.remindersBetween(from: Instant, until: Instant, zone: ZoneId, lead: Duration): Sequence<Reminder> {
        // A day either side, so a start near midnight or a daylight-saving shift can't fall outside.
        val firstDay = from.plus(lead).atZone(zone).toLocalDate().minusDays(1)
        val lastDay = until.plus(lead).atZone(zone).toLocalDate().plusDays(1)
        return generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .filter { it.dayOfWeek in days && it != skippedOn }
            .map { day ->
                val start = startOn(day, zone)
                Reminder(this, startsAt = start, remindAt = start.minus(lead))
            }
            .filter { !it.remindAt.isBefore(from) && !it.remindAt.isAfter(until) }
    }

    /** The workout a snooze belongs to: the latest one whose reminder came at or before it. */
    private fun WorkoutSchedule.startSnoozedAt(snoozedUntil: Instant, zone: ZoneId, lead: Duration): Instant {
        val lastDay = snoozedUntil.plus(lead).atZone(zone).toLocalDate().plusDays(1)
        return generateSequence(lastDay) { it.minusDays(1) }
            .take(SEARCH_DAYS.toInt())
            .filter { it.dayOfWeek in days }
            .map { startOn(it, zone) }
            .firstOrNull { !it.minus(lead).isAfter(snoozedUntil) }
            ?: snoozedUntil
    }
}
