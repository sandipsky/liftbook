package com.example.liftbook.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.liftbook.domain.model.FirstDayOfWeek
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

    /** When a backup was last exported (FR-6.3). Kept here, but not a setting: it isn't exported. */
    val lastExportedAt = longPreferencesKey("lastExportedAt")
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
)

/** Stores the settings a backup carries (FR-6.3): the rest, the week start and the theme. */
internal fun MutablePreferences.setBackedUpSettings(preferences: UserPreferences) {
    this[SettingsKeys.defaultRestSeconds] = preferences.defaultRestSeconds
    this[SettingsKeys.firstDayOfWeek] = preferences.firstDayOfWeek.name
    this[SettingsKeys.themeMode] = preferences.themeMode.name
}

internal val Preferences.lastExportedAt: Instant?
    get() = this[SettingsKeys.lastExportedAt]?.let(Instant::ofEpochMilli)

internal inline fun <reified T : Enum<T>> enumOrNull(stored: String?): T? =
    stored?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
