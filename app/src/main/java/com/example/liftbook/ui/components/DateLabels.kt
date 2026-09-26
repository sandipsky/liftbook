package com.example.liftbook.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A time of day in the user's own clock format: "18:30", or "6:30 PM" with a 12-hour clock. */
@Composable
fun timeOfDayText(time: LocalTime): String {
    val locale = LocalConfiguration.current.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hm"
    val formatter = remember(locale, skeleton) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    return formatter.format(time)
}

/**
 * A workout date in the locale's own order: "Tue, 12 Sep" (or "Tue, Sep 12"), with the year
 * only when it isn't the current one.
 */
@Composable
fun workoutDateText(date: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val skeleton = if (date.year == today.year) "EEEdMMM" else "EEEdMMMy"
    val formatter = remember(locale, skeleton) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    return formatter.format(date)
}

/** "Today", "Yesterday", "3 days ago", "2 weeks ago" — or null once an absolute date reads better. */
@Composable
fun relativeDayText(date: LocalDate, today: LocalDate): String? {
    val days = ChronoUnit.DAYS.between(date, today).toInt()
    val weeks = days / 7
    return when {
        days < 0 -> null
        days == 0 -> stringResource(R.string.date_today)
        days == 1 -> stringResource(R.string.date_yesterday)
        days < 7 -> pluralStringResource(R.plurals.date_days_ago, days, days)
        weeks <= MAX_RELATIVE_WEEKS -> pluralStringResource(R.plurals.date_weeks_ago, weeks, weeks)
        else -> null
    }
}

private const val MAX_RELATIVE_WEEKS = 8
