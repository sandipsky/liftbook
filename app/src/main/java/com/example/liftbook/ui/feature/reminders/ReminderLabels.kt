package com.example.liftbook.ui.feature.reminders

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.ui.components.weekdayText
import com.example.liftbook.ui.components.workoutDurationSpoken
import com.example.liftbook.ui.components.workoutDurationText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** The seven days in the order the user's week runs, from its first day (FR-6.2). */
fun FirstDayOfWeek.week(): List<DayOfWeek> = (0L until DAYS_PER_WEEK).map { dayOfWeek.plus(it) }

/**
 * The days a workout is on, in the order the week runs: "Mon · Wed · Fri", or a name for a
 * run everyone knows — "Weekdays", "Weekends", "Every day".
 */
@Composable
fun daysText(days: Set<DayOfWeek>, firstDayOfWeek: FirstDayOfWeek): String = when (days) {
    DayOfWeek.entries.toSet() -> stringResource(R.string.days_every_day)
    WEEKDAYS -> stringResource(R.string.days_weekdays)
    WEEKENDS -> stringResource(R.string.days_weekends)
    else -> {
        val locale = LocalConfiguration.current.locales[0]
        firstDayOfWeek.week().filter { it in days }
            .joinToString(stringResource(R.string.list_separator)) { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) }
    }
}

/** [daysText] for TalkBack, each day in full: "Monday, Wednesday, Friday". */
@Composable
fun daysSpoken(days: Set<DayOfWeek>, firstDayOfWeek: FirstDayOfWeek): String {
    val locale = LocalConfiguration.current.locales[0]
    return firstDayOfWeek.week().filter { it in days }
        .joinToString(stringResource(R.string.list_separator_spoken)) { it.getDisplayName(TextStyle.FULL_STANDALONE, locale) }
}

/** When a reminder comes: "At the start", "10 min before", "1 h 30 min before". */
@Composable
fun leadText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.lead_at_start) else stringResource(R.string.lead_before, workoutDurationText(minutes * SECONDS_PER_MINUTE))

/** [leadText] for TalkBack: "10 minutes before". */
@Composable
fun leadSpoken(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.lead_at_start) else stringResource(R.string.lead_before, workoutDurationSpoken(minutes * SECONDS_PER_MINUTE))

/** A day near today as people say it: "Today", "Tomorrow", then the weekday, "Wed". */
@Composable
fun nearDayText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> weekdayText(date)
}

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private const val DAYS_PER_WEEK = 7L
private const val SECONDS_PER_MINUTE = 60L
