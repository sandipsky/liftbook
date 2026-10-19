package com.example.liftbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.repository.AccountRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.usecase.CloudBackups
import com.example.liftbook.domain.usecase.WorkoutReminders
import com.example.liftbook.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the whole app needs before it draws: the theme the user picked (FR-6.2) and where to open
 * (FR-8.8). Also where the
 * work that runs for as long as the app does is set going: the reminder alarms and the cloud backup.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    reminders: WorkoutReminders,
    cloudBackups: CloudBackups,
    accountRepository: AccountRepository,
) : ViewModel() {

    /** Null until the setting has been read, so the first frame is already in the right theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.userPreferences
        .map { it.themeMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Where the app opens (FR-8.8): the sign-in page on the first launch, when there's somewhere
     * to sign in to and nobody is; Home after that, and always once decided. Null until known.
     */
    val startDestination: StateFlow<Route?> = combine(settingsRepository.hasSeenWelcome, accountRepository.account) { seen, account ->
        if (seen || account != null || !accountRepository.isAvailable) Route.Home else Route.Welcome
    }
        .take(1)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // The cloud copy follows the phone's data for as long as the process lives (FR-8.5).
        cloudBackups.start()
        // Force-stopping the app clears its alarms and sends nothing, so opening it sets them again (FR-7.6).
        viewModelScope.launch {
            try {
                reminders.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The alarm or the next schedule edit syncs again; opening the app mustn't fail over it.
            }
        }
    }
}
