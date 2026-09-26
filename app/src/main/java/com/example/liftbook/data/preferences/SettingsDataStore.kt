package com.example.liftbook.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ScheduleDraft
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import java.time.Instant
import java.util.Locale

/** File name of the Preferences DataStore that holds user settings. */
const val SETTINGS_DATASTORE_NAME = "settings"

internal object SettingsKeys {
    val weightUnit = stringPreferencesKey("weightUnit")
    val defaultRestSeconds = intPreferencesKey("defaultRestSeconds")
    val firstDayOfWeek = stringPreferencesKey("firstDayOfWeek")
    val themeMode = stringPreferencesKey("themeMode")
    val remindersEnabled = booleanPreferencesKey("remindersEnabled")
    val reminderLeadMinutes = intPreferencesKey("defaultReminderLeadMinutes")
    val snoozeMinutes = intPreferencesKey("snoozeMinutes")

    /** When a backup was last exported (FR-6.3). Kept here, but not a setting: it isn't exported. */
    val lastExportedAt = longPreferencesKey("lastExportedAt")

    /** When the reminder alarm is set to go off (FR-7.6). Not a setting either: it's the alarm's own bookkeeping. */
    val reminderAlarmAt = longPreferencesKey("reminderAlarmAt")
}

/**
 * Unknown or missing values fall back to defaults instead of failing. Until the user picks a
 * week start, it's [locale]'s.
 */
internal fun Preferences.toUserPreferences(locale: Locale = Locale.getDefault()): UserPreferences = UserPreferences(
    weightUnit = enumOrNull<WeightUnit>(this[SettingsKeys.weightUnit]) ?: WeightUnit.KG,
    defaultRestSeconds = this[SettingsKeys.defaultRestSeconds]?.takeIf { it >= 0 } ?: UserPreferences.DEFAULT_REST_SECONDS,
    firstDayOfWeek = enumOrNull<FirstDayOfWeek>(this[SettingsKeys.firstDayOfWeek]) ?: FirstDayOfWeek.defaultFor(locale),
    themeMode = enumOrNull<ThemeMode>(this[SettingsKeys.themeMode]) ?: ThemeMode.SYSTEM,
    remindersEnabled = this[SettingsKeys.remindersEnabled] ?: true,
    reminderLeadMinutes = this[SettingsKeys.reminderLeadMinutes]?.takeIf(::isLeadMinutes)
        ?: UserPreferences.DEFAULT_REMINDER_LEAD_MINUTES,
    snoozeMinutes = this[SettingsKeys.snoozeMinutes]?.takeIf(::isSnoozeMinutes) ?: UserPreferences.DEFAULT_SNOOZE_MINUTES,
)

/** Stores the settings a backup carries (FR-6.3): the rest, the week start, the theme and the reminder settings. */
internal fun MutablePreferences.setBackedUpSettings(preferences: UserPreferences) {
    this[SettingsKeys.defaultRestSeconds] = preferences.defaultRestSeconds
    this[SettingsKeys.firstDayOfWeek] = preferences.firstDayOfWeek.name
    this[SettingsKeys.themeMode] = preferences.themeMode.name
    this[SettingsKeys.remindersEnabled] = preferences.remindersEnabled
    this[SettingsKeys.reminderLeadMinutes] = preferences.reminderLeadMinutes
    this[SettingsKeys.snoozeMinutes] = preferences.snoozeMinutes
}

/** A lead time a reminder can have (FR-7.2): from the start itself up to a day before. */
internal fun isLeadMinutes(minutes: Int): Boolean = minutes in 0..ScheduleDraft.MAX_LEAD_MINUTES

/** A snooze has to put the reminder off by something, and less than the day (FR-7.4). */
internal fun isSnoozeMinutes(minutes: Int): Boolean = minutes in 1..ScheduleDraft.MAX_LEAD_MINUTES

internal val Preferences.lastExportedAt: Instant?
    get() = this[SettingsKeys.lastExportedAt]?.let(Instant::ofEpochMilli)

internal inline fun <reified T : Enum<T>> enumOrNull(stored: String?): T? =
    stored?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
