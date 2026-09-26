package com.example.liftbook.testing

import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: UserPreferences = UserPreferences()) : SettingsRepository {
    override val userPreferences = MutableStateFlow(initial)

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        userPreferences.update { it.copy(defaultRestSeconds = seconds) }
    }
}
