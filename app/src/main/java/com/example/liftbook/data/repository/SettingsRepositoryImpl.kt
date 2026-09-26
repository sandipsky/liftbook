package com.example.liftbook.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.liftbook.data.preferences.SettingsKeys
import com.example.liftbook.data.preferences.toUserPreferences
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        // An unreadable settings file must not take the app down; defaults are always valid.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        require(seconds >= 0) { "A rest can't be negative" }
        dataStore.edit { it[SettingsKeys.defaultRestSeconds] = seconds }
    }

    override suspend fun setFirstDayOfWeek(firstDayOfWeek: FirstDayOfWeek) {
        dataStore.edit { it[SettingsKeys.firstDayOfWeek] = firstDayOfWeek.name }
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[SettingsKeys.themeMode] = themeMode.name }
    }
}
