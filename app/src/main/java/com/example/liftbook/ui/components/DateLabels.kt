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
import java.time.YearMonth
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

/** A date in full, as TalkBack should say it: "Tuesday, 22 September", with the year when it isn't this one. */
@Composable
fun spokenDateText(date: LocalDate, today: LocalDate): String =
    dateText(date, if (date.year == today.year) "EEEEdMMMM" else "EEEEdMMMMy")

/** The short weekday: "Tue". */
@Composable
fun weekdayText(date: LocalDate): String = dateText(date, "EEE")

/** The day of the month, in the locale's own numerals: "22". */
@Composable
fun dayOfMonthText(date: LocalDate): String = dateText(date, "d")

/** A month's name, with the year only when it isn't this one: "September", "September 2025". */
@Composable
fun monthText(month: YearMonth, today: LocalDate): String = dateText(month.atDay(1), if (month.year == today.year) "LLLL" else "MMMMy")

@Composable
private fun dateText(date: LocalDate, skeleton: String): String {
    val locale = LocalConfiguration.current.locales[0]
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
