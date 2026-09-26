package com.example.liftbook.ui.feature.settings

import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SettingsUiState(
    val isLoading: Boolean = true,
    /** 0 means the rest timer is off (FR-3.5). */
    val defaultRestSeconds: Int = UserPreferences.DEFAULT_REST_SECONDS,
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.MONDAY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The global reminder switch (FR-7.7). */
    val remindersEnabled: Boolean = true,
    /** Scheduled workouts a week across the entries that are on (FR-7.1). */
    val remindersPerWeek: Int = 0,
    /** When a backup was last exported; null if never. */
    val lastExportedAt: Instant? = null,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed interface SettingsAction {
    data class SetDefaultRest(val seconds: Int) : SettingsAction

    data class SetFirstDayOfWeek(val firstDayOfWeek: FirstDayOfWeek) : SettingsAction

    data class SetTheme(val themeMode: ThemeMode) : SettingsAction

    // Navigation; handled by the route.
    data object OpenReminders : SettingsAction

    data object OpenDataManagement : SettingsAction

    data object NavigateUp : SettingsAction
}

sealed interface SettingsEvent {
    data object SaveFailed : SettingsEvent
}
