package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val userPreferences: Flow<UserPreferences>

    /** The rest after a set for exercises without their own; 0 turns the timer off (FR-3.5). */
    suspend fun setDefaultRestSeconds(seconds: Int)

    suspend fun setFirstDayOfWeek(firstDayOfWeek: FirstDayOfWeek)

    suspend fun setThemeMode(themeMode: ThemeMode)
}
