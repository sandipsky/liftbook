package com.example.liftbook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.domain.model.Routine
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * When a routine was last done (FR-2.4): "Last done yesterday", "Last done 3 days ago", then an
 * absolute date once that reads better — or "Not done yet".
 */
@Composable
fun lastDoneText(routine: Routine, today: LocalDate, zone: ZoneId): String {
    val date = routine.lastPerformedAt?.atZone(zone)?.toLocalDate()
        ?: return stringResource(R.string.routine_never_done)
    val days = ChronoUnit.DAYS.between(date, today).toInt()
    val weeks = days / 7
    return when {
        days < 0 -> stringResource(R.string.routine_last_done_date, workoutDateText(date, today))
        days == 0 -> stringResource(R.string.routine_last_done_today)
        days == 1 -> stringResource(R.string.routine_last_done_yesterday)
        days < 7 -> pluralStringResource(R.plurals.routine_last_done_days_ago, days, days)
        weeks <= MAX_RELATIVE_WEEKS -> pluralStringResource(R.plurals.routine_last_done_weeks_ago, weeks, weeks)
        else -> stringResource(R.string.routine_last_done_date, workoutDateText(date, today))
    }
}

/** "6 exercises · Last done 3 days ago". */
@Composable
fun routineMetaText(routine: Routine, today: LocalDate, zone: ZoneId): String = stringResource(
    R.string.routine_meta,
    pluralStringResource(R.plurals.routine_exercise_count, routine.exercises.size, routine.exercises.size),
    lastDoneText(routine, today, zone),
)

private const val MAX_RELATIVE_WEEKS = 8
