package com.example.liftbook.ui.feature.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.domain.repository.BackupRepository
import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.domain.usecase.WorkoutReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Backups and clearing (FR-6.3–6.5). One thing runs at a time. A replace or a clear takes the
 * workout in progress with it, so the rest alert it may have scheduled goes too. Every import
 * and clear changes the schedule, so the reminders follow it (FR-7.6).
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val restTimerScheduler: RestTimerScheduler,
    private val reminders: WorkoutReminders,
    private val clock: Clock,
) : ViewModel() {

    /** The word typed to confirm clearing everything (FR-6.5). */
    val clearConfirmation = TextFieldState()

    private val session = MutableStateFlow(Session())

    val uiState: StateFlow<DataManagementUiState> = combine(
        backupRepository.observeCounts(),
        backupRepository.lastExportedAt,
        session,
    ) { counts, lastExportedAt, session ->
        DataManagementUiState(
            isLoading = false,
            counts = counts,
            lastExportedAt = lastExportedAt,
            operation = session.operation,
            importing = session.importing,
            isConfirmingReplace = session.isConfirmingReplace,
            isConfirmingClear = session.isConfirmingClear,
            today = LocalDate.now(clock),
            zone = clock.zone,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DataManagementUiState(today = LocalDate.now(clock), zone = clock.zone),
    )

    private val _events = Channel<DataManagementEvent>(Channel.BUFFERED)
    val events: Flow<DataManagementEvent> = _events.receiveAsFlow()

    fun onAction(action: DataManagementAction) {
        when (action) {
            is DataManagementAction.ExportTo -> perform(BackupOperation.EXPORTING, failure = DataManagementEvent.ExportFailed) {
                DataManagementEvent.Exported(backupRepository.export(action.destination))
            }
            is DataManagementAction.ReadBackup -> perform(BackupOperation.READING, failure = DataManagementEvent.ImportFailed) {
                val summary = backupRepository.inspect(action.source)
                // On an empty phone nothing is lost by replacing, and it brings the settings over too.
                val mode = if (backupRepository.observeCounts().first().isEmpty) ImportMode.REPLACE else ImportMode.MERGE
                session.update { it.copy(importing = ImportDraft(action.source, summary, mode)) }
                null
            }
            is DataManagementAction.ChooseImportMode -> session.update { it.copy(importing = it.importing?.copy(mode = action.mode)) }
            DataManagementAction.StartImport -> {
                val draft = session.value.importing ?: return
                if (draft.mode == ImportMode.REPLACE) session.update { it.copy(isConfirmingReplace = true) } else importBackup(draft)
            }
            DataManagementAction.ConfirmReplace -> {
                session.update { it.copy(isConfirmingReplace = false) }
                session.value.importing?.let(::importBackup)
            }
            DataManagementAction.DismissReplace -> session.update { it.copy(isConfirmingReplace = false) }
            DataManagementAction.DismissImport -> session.update { if (it.operation == null) it.copy(importing = null) else it }
            DataManagementAction.ClearAll -> {
                if (session.value.operation != null) return
                clearConfirmation.clearText()
                session.update { it.copy(isConfirmingClear = true) }
            }
            DataManagementAction.ConfirmClear -> {
                session.update { it.copy(isConfirmingClear = false) }
                perform(BackupOperation.CLEARING, failure = DataManagementEvent.ClearFailed) {
                    backupRepository.clearAll()
                    restTimerScheduler.cancel()
                    syncReminders()
                    DataManagementEvent.Cleared
                }
            }
            DataManagementAction.DismissClear -> session.update { it.copy(isConfirmingClear = false) }
            // The file pickers and navigation; handled by the route.
            DataManagementAction.Export, DataManagementAction.Import, DataManagementAction.NavigateUp -> Unit
        }
    }

    private fun importBackup(draft: ImportDraft) = perform(BackupOperation.IMPORTING, failure = DataManagementEvent.ImportFailed) {
        val added = backupRepository.import(draft.source, draft.mode)
        session.update { it.copy(importing = null) }
        syncReminders()
        when (draft.mode) {
            ImportMode.MERGE -> DataManagementEvent.Merged(added)
            ImportMode.REPLACE -> {
                restTimerScheduler.cancel()
                DataManagementEvent.Replaced
            }
        }
    }

    /**
     * Runs [work] unless something else is running. Leaving the screen doesn't cut it short:
     * the database rolls back a transaction that's interrupted, but not the settings written after.
     */
    private fun perform(operation: BackupOperation, failure: DataManagementEvent, work: suspend () -> DataManagementEvent?) {
        if (session.value.operation != null) return
        session.update { it.copy(operation = operation) }
        viewModelScope.launch {
            val event = withContext(NonCancellable) {
                try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BackupException) {
                    session.update { it.copy(importing = null) }
                    DataManagementEvent.CantImport(e.problem)
                } catch (e: Exception) {
                    session.update { it.copy(importing = null) }
                    failure
                } finally {
                    session.update { it.copy(operation = null) }
                }
            }
            event?.let { _events.send(it) }
        }
    }

    /**
     * The data is in; the alarm following it is secondary. If setting it fails, the next sync
     * — at the latest when the app opens — sets it, so it doesn't fail what already succeeded.
     */
    private suspend fun syncReminders() {
        try {
            reminders.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // See above.
        }
    }

    private data class Session(
        val operation: BackupOperation? = null,
        val importing: ImportDraft? = null,
        val isConfirmingReplace: Boolean = false,
        val isConfirmingClear: Boolean = false,
    )
}
