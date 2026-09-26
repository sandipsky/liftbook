package com.example.liftbook.data.preferences

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SettingsDataStoreTest {

    @Test
    fun `missing settings fall back to defaults`() {
        assertEquals(
            UserPreferences(weightUnit = WeightUnit.KG, firstDayOfWeek = FirstDayOfWeek.MONDAY, themeMode = ThemeMode.SYSTEM),
            emptyPreferences().toUserPreferences(Locale.UK),
        )
    }

    @Test
    fun `a stored unit is read back`() {
        assertEquals(WeightUnit.LB, preferencesOf(SettingsKeys.weightUnit to "LB").toUserPreferences().weightUnit)
    }

    @Test
    fun `an unknown stored value falls back instead of crashing`() {
        val stored = preferencesOf(
            SettingsKeys.weightUnit to "STONE",
            SettingsKeys.firstDayOfWeek to "WEDNESDAY",
            SettingsKeys.themeMode to "SEPIA",
        ).toUserPreferences(Locale.UK)

        assertEquals(WeightUnit.KG, stored.weightUnit)
        assertEquals(FirstDayOfWeek.MONDAY, stored.firstDayOfWeek)
        assertEquals(ThemeMode.SYSTEM, stored.themeMode)
    }

    @Test
    fun `the default rest is 90 seconds until set, and 0 means off`() {
        assertEquals(90, emptyPreferences().toUserPreferences().defaultRestSeconds)
        assertEquals(150, preferencesOf(SettingsKeys.defaultRestSeconds to 150).toUserPreferences().defaultRestSeconds)
        assertEquals(0, preferencesOf(SettingsKeys.defaultRestSeconds to 0).toUserPreferences().defaultRestSeconds)
        assertEquals(90, preferencesOf(SettingsKeys.defaultRestSeconds to -5).toUserPreferences().defaultRestSeconds)
    }

    @Test
    fun `until picked, weeks start where the locale starts them, or Monday when that's neither`() {
        assertEquals(FirstDayOfWeek.SUNDAY, emptyPreferences().toUserPreferences(Locale.US).firstDayOfWeek)
        assertEquals(FirstDayOfWeek.MONDAY, emptyPreferences().toUserPreferences(Locale.GERMANY).firstDayOfWeek)
        // Egypt starts weeks on Saturday, which the setting doesn't offer.
        assertEquals(FirstDayOfWeek.MONDAY, emptyPreferences().toUserPreferences(Locale.forLanguageTag("ar-EG")).firstDayOfWeek)
    }

    @Test
    fun `a picked week start and theme win over the locale`() {
        val stored = preferencesOf(
            SettingsKeys.firstDayOfWeek to "MONDAY",
            SettingsKeys.themeMode to "DARK",
        ).toUserPreferences(Locale.US)

        assertEquals(FirstDayOfWeek.MONDAY, stored.firstDayOfWeek)
        assertEquals(ThemeMode.DARK, stored.themeMode)
    }

    @Test
    fun `restoring a backup's settings writes all three, and they read back`() {
        val preferences = UserPreferences(defaultRestSeconds = 120, firstDayOfWeek = FirstDayOfWeek.SUNDAY, themeMode = ThemeMode.LIGHT)
        val stored = mutablePreferencesOf().apply { setBackedUpSettings(preferences) }

        assertEquals(preferences, stored.toUserPreferences(Locale.UK))
    }
}
