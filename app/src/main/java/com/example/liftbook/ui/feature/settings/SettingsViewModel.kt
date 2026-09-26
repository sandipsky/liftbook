package com.example.liftbook.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.ReminderTimes
import com.example.liftbook.domain.repository.BackupRepository
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Settings (FR-6.2): the default rest, where weeks start and the theme — each applied as it's
 * picked, with nothing to save — and the ways to reminders (FR-7) and backups (FR-6.3–6.5).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    backupRepository: BackupRepository,
    scheduleRepository: ScheduleRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.userPreferences,
        backupRepository.lastExportedAt,
        scheduleRepository.observeSchedules(),
    ) { preferences, lastExportedAt, schedules ->
        SettingsUiState(
            isLoading = false,
            defaultRestSeconds = preferences.defaultRestSeconds,
            firstDayOfWeek = preferences.firstDayOfWeek,
            themeMode = preferences.themeMode,
            remindersEnabled = preferences.remindersEnabled,
            remindersPerWeek = ReminderTimes.perWeek(schedules),
            lastExportedAt = lastExportedAt,
            today = LocalDate.now(clock),
            zone = clock.zone,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(today = LocalDate.now(clock), zone = clock.zone),
    )

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetDefaultRest -> save { settingsRepository.setDefaultRestSeconds(action.seconds) }
            is SettingsAction.SetFirstDayOfWeek -> save { settingsRepository.setFirstDayOfWeek(action.firstDayOfWeek) }
            is SettingsAction.SetTheme -> save { settingsRepository.setThemeMode(action.themeMode) }
            // Navigation; handled by the route.
            SettingsAction.OpenReminders, SettingsAction.OpenDataManagement, SettingsAction.NavigateUp -> Unit
        }
    }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(SettingsEvent.SaveFailed)
            }
        }
    }
}
