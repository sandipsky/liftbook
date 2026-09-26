package com.example.liftbook.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.domain.calculator.TimeOfDay
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.WeightUnit
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** The name an empty workout gets from when it starts: "Evening workout". */
@StringRes
fun TimeOfDay.workoutNameRes(): Int = when (this) {
    TimeOfDay.MORNING -> R.string.workout_name_morning
    TimeOfDay.AFTERNOON -> R.string.workout_name_afternoon
    TimeOfDay.EVENING -> R.string.workout_name_evening
    TimeOfDay.NIGHT -> R.string.workout_name_night
}

/** How long a workout has run at [now], as a clock reading: "32:14", "1:05:30". */
fun elapsedText(startedAt: Instant, now: Instant): String = formatDuration(elapsedSeconds(startedAt, now))

fun elapsedSeconds(startedAt: Instant, now: Instant): Int =
    Duration.between(startedAt, now).seconds.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

/**
 * Whole seconds of rest left at [now], rounded up as a countdown reads: 0.4 s left shows 0:01,
 * and 0:00 means it's over.
 */
fun restSecondsLeft(rest: RestTimer, now: Instant): Int =
    ((rest.remaining(now).toMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

private const val MILLIS_PER_SECOND = 1_000L

/** How long a workout lasted, to the minute, as a list reads it: "1 h 2 min", "45 min", "2 h", "<1 min". */
@Composable
fun workoutDurationText(totalSeconds: Long): String {
    val minutes = (totalSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        minutes == 0 -> stringResource(R.string.duration_short_under_minute)
        hours == 0 -> stringResource(R.string.duration_short_minutes, rest)
        rest == 0 -> stringResource(R.string.duration_short_hours, hours)
        else -> stringResource(R.string.duration_short_hours_minutes, hours, rest)
    }
}

/** [workoutDurationText] for TalkBack: "1 hour 2 minutes". */
@Composable
fun workoutDurationSpoken(totalSeconds: Long): String {
    val minutes = (totalSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE).coerceAtMost(Int.MAX_VALUE.toLong() / SECONDS_PER_MINUTE).toInt()
    return if (minutes == 0) stringResource(R.string.duration_under_minute_spoken) else spokenDuration(minutes * SECONDS_PER_MINUTE.toInt())
}

/** Hours and minutes as a clock reading, for totals shown with an "h": 9 h 20 min → "9:20". */
fun hoursMinutesText(totalSeconds: Long): String {
    val minutes = totalSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE
    return String.format(Locale.getDefault(), "%d:%02d", minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
}

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60

/** A rest duration as a clock reading, "1:30", or "Off" for no timer. */
@Composable
fun restDurationText(seconds: Int): String = if (seconds > 0) formatDuration(seconds) else stringResource(R.string.rest_off)

/** [restDurationText] for TalkBack: "1 minute 30 seconds", or "Off". */
@Composable
fun restDurationSpoken(seconds: Int): String = if (seconds > 0) spokenDuration(seconds) else stringResource(R.string.rest_off)

/**
 * A weight in the user's unit with its label: "100 kg", "225 lb". An estimate reads better with
 * fewer decimals — [maxFractionDigits] = 1 shows an estimated 1RM as "116.7 kg".
 */
@Composable
fun weightWithUnit(kg: Double, unit: WeightUnit, maxFractionDigits: Int = 2): String = stringResource(
    R.string.weight_with_unit,
    displayWeight(kg, unit, maxFractionDigits = maxFractionDigits),
    stringResource(unit.weightLabelRes()),
)
