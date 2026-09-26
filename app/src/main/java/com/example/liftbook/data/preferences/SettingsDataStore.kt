package com.example.liftbook.data.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit

/** File name of the Preferences DataStore that holds user settings. */
const val SETTINGS_DATASTORE_NAME = "settings"

internal object SettingsKeys {
    val weightUnit = stringPreferencesKey("weightUnit")
    val defaultRestSeconds = intPreferencesKey("defaultRestSeconds")
}

/** Unknown or missing values fall back to defaults instead of failing. */
internal fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
    weightUnit = this[SettingsKeys.weightUnit]
        ?.let { stored -> WeightUnit.entries.firstOrNull { it.name == stored } }
        ?: WeightUnit.KG,
    defaultRestSeconds = this[SettingsKeys.defaultRestSeconds]?.takeIf { it >= 0 } ?: UserPreferences.DEFAULT_REST_SECONDS,
)
