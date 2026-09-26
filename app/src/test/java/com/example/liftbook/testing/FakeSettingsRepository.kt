package com.example.liftbook.testing

import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: UserPreferences = UserPreferences()) : SettingsRepository {
    override val userPreferences = MutableStateFlow(initial)

    /** When set, every write throws it instead of saving. */
    var failure: Exception? = null

    override suspend fun setDefaultRestSeconds(seconds: Int) = write { it.copy(defaultRestSeconds = seconds) }

    override suspend fun setFirstDayOfWeek(firstDayOfWeek: FirstDayOfWeek) = write { it.copy(firstDayOfWeek = firstDayOfWeek) }

    override suspend fun setThemeMode(themeMode: ThemeMode) = write { it.copy(themeMode = themeMode) }

    override suspend fun setRemindersEnabled(enabled: Boolean) = write { it.copy(remindersEnabled = enabled) }

    override suspend fun setReminderLeadMinutes(minutes: Int) = write { it.copy(reminderLeadMinutes = minutes) }

    override suspend fun setSnoozeMinutes(minutes: Int) = write { it.copy(snoozeMinutes = minutes) }

    private fun write(change: (UserPreferences) -> UserPreferences) {
        failure?.let { throw it }
        userPreferences.update(change)
    }
}
