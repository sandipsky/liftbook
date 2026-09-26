package com.example.liftbook.ui.feature.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.example.liftbook.R
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.ui.components.relativeDayText
import com.example.liftbook.ui.components.shortDateText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/** The weekday in full, in the user's language, as a label starts: "Monday", "Lundi". */
@Composable
fun FirstDayOfWeek.label(): String {
    val locale = LocalConfiguration.current.locales[0]
    return dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@StringRes
fun ThemeMode.spokenRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system_spoken
    ThemeMode.LIGHT -> R.string.settings_theme_light_spoken
    ThemeMode.DARK -> R.string.settings_theme_dark_spoken
}

/** When the last backup was made, as the day it was: "Today", "3 days ago", "12 Sep". */
@Composable
fun backupDayText(exportedAt: Instant, today: LocalDate, zone: ZoneId): String {
    val date = exportedAt.atZone(zone).toLocalDate()
    return relativeDayText(date, today) ?: shortDateText(date, today)
}

/**
 * What a phone or a backup holds, joined: "142 workouts · 6 routines · 31 weigh-ins". Kinds
 * with none are left out. Takes [Resources] because snackbars are worded outside composition.
 */
fun Resources.dataCountsText(counts: DataCounts): String = listOfNotNull(
    counts.workouts.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_count_workouts, it, it) },
    counts.routines.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_count_routines, it, it) },
    counts.customExercises.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_count_exercises, it, it) },
    counts.weighIns.takeIf { it > 0 }?.let { getQuantityString(R.plurals.data_count_weigh_ins, it, it) },
).joinToString(getString(R.string.list_separator))
