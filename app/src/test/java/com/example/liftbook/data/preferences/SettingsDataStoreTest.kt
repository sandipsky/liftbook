package com.example.liftbook.data.preferences

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDataStoreTest {

    @Test
    fun `missing settings fall back to defaults`() {
        assertEquals(UserPreferences(weightUnit = WeightUnit.KG), emptyPreferences().toUserPreferences())
    }

    @Test
    fun `a stored unit is read back`() {
        assertEquals(WeightUnit.LB, preferencesOf(SettingsKeys.weightUnit to "LB").toUserPreferences().weightUnit)
    }

    @Test
    fun `an unknown stored value falls back instead of crashing`() {
        assertEquals(WeightUnit.KG, preferencesOf(SettingsKeys.weightUnit to "STONE").toUserPreferences().weightUnit)
    }

    @Test
    fun `the default rest is 90 seconds until set, and 0 means off`() {
        assertEquals(90, emptyPreferences().toUserPreferences().defaultRestSeconds)
        assertEquals(150, preferencesOf(SettingsKeys.defaultRestSeconds to 150).toUserPreferences().defaultRestSeconds)
        assertEquals(0, preferencesOf(SettingsKeys.defaultRestSeconds to 0).toUserPreferences().defaultRestSeconds)
        assertEquals(90, preferencesOf(SettingsKeys.defaultRestSeconds to -5).toUserPreferences().defaultRestSeconds)
    }
}
