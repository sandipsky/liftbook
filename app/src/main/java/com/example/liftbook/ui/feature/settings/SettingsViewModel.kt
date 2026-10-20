package com.example.liftbook.ui.feature.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.ReminderTimes
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.repository.AccountRepository
import com.example.liftbook.domain.repository.BackupRepository
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.usecase.CloudBackups
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Settings (FR-6.2): the default rest, where weeks start and the theme — each applied as it's
 * picked, with nothing to save — the ways to the account (FR-8), reminders (FR-7) and backups
 * (FR-6.3–6.5), and deleting everything (FR-6.5), which is also offered here so it needn't be
 * found behind the backup page.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    backupRepository: BackupRepository,
    scheduleRepository: ScheduleRepository,
    accountRepository: AccountRepository,
    private val cloudBackups: CloudBackups,
    private val clock: Clock,
) : ViewModel() {

    /** The word typed to confirm deleting everything (FR-6.5). */
    val clearConfirmation = TextFieldState()

    private val deletion = MutableStateFlow(Deletion())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.userPreferences,
        backupRepository.lastExportedAt,
        scheduleRepository.observeSchedules(),
        accountRepository.account,
        deletion,
    ) { preferences, lastExportedAt, schedules, account, deletion ->
        SettingsUiState(
            isLoading = false,
            defaultRestSeconds = preferences.defaultRestSeconds,
            firstDayOfWeek = preferences.firstDayOfWeek,
            themeMode = preferences.themeMode,
            remindersEnabled = preferences.remindersEnabled,
            remindersPerWeek = ReminderTimes.perWeek(schedules),
            lastExportedAt = lastExportedAt,
            accountsAvailable = accountRepository.isAvailable,
            accountEmail = account?.email,
            isConfirmingDelete = deletion.isConfirming,
            isDeleting = deletion.isRunning,
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
            SettingsAction.DeleteAllData -> if (!deletion.value.isRunning) {
                clearConfirmation.clearText()
                deletion.update { it.copy(isConfirming = true) }
            }
            SettingsAction.ConfirmDeleteAllData -> {
                deletion.update { it.copy(isConfirming = false) }
                deleteAll()
            }
            SettingsAction.DismissDeleteAllData -> deletion.update { it.copy(isConfirming = false) }
            // Navigation; handled by the route.
            SettingsAction.OpenReminders, SettingsAction.OpenDataManagement, SettingsAction.OpenAccount, SettingsAction.NavigateUp -> Unit
        }
    }

    /** Everything on the phone and the account's backup online (FR-6.5). Leaving the page doesn't cut it short. */
    private fun deleteAll() {
        if (deletion.value.isRunning) return
        deletion.update { it.copy(isRunning = true) }
        viewModelScope.launch {
            val event = withContext(NonCancellable) {
                try {
                    cloudBackups.eraseAll()
                    SettingsEvent.DataDeleted
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AccountException) {
                    SettingsEvent.CloudUnreachable
                } catch (e: Exception) {
                    SettingsEvent.DeleteFailed
                } finally {
                    deletion.update { it.copy(isRunning = false) }
                }
            }
            _events.send(event)
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

    private data class Deletion(val isConfirming: Boolean = false, val isRunning: Boolean = false)
}
