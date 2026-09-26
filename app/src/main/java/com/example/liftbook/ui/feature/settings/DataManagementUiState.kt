package com.example.liftbook.ui.feature.settings

import com.example.liftbook.domain.model.BackupProblem
import com.example.liftbook.domain.model.BackupSummary
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.ImportMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DataManagementUiState(
    val isLoading: Boolean = true,
    /** What's on this phone now. */
    val counts: DataCounts = DataCounts(),
    /** When a backup was last exported; null if never. */
    val lastExportedAt: Instant? = null,
    /** What's running, if anything; nothing else starts until it's done. */
    val operation: BackupOperation? = null,
    /** A backup that's been read and is waiting for merge or replace (FR-6.4). */
    val importing: ImportDraft? = null,
    val isConfirmingReplace: Boolean = false,
    val isConfirmingClear: Boolean = false,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isBusy: Boolean get() = operation != null

    /** What the file picker suggests calling a new backup: "liftbook-backup-2026-09-26.json". */
    val backupFileName: String get() = "liftbook-backup-$today.json"
}

enum class BackupOperation { EXPORTING, READING, IMPORTING, CLEARING }

/** A backup file, read and checked, and how it's going to be imported. */
data class ImportDraft(
    val source: DocumentUri,
    val summary: BackupSummary,
    val mode: ImportMode,
)

sealed interface DataManagementAction {
    /** Ask where to save a backup; the route opens the file picker. */
    data object Export : DataManagementAction

    data class ExportTo(val destination: DocumentUri) : DataManagementAction

    /** Ask which backup to import; the route opens the file picker. */
    data object Import : DataManagementAction

    data class ReadBackup(val source: DocumentUri) : DataManagementAction

    data class ChooseImportMode(val mode: ImportMode) : DataManagementAction

    /** From the import sheet: merge now, or ask before replacing. */
    data object StartImport : DataManagementAction

    data object ConfirmReplace : DataManagementAction

    data object DismissReplace : DataManagementAction

    data object DismissImport : DataManagementAction

    data object ClearAll : DataManagementAction

    data object ConfirmClear : DataManagementAction

    data object DismissClear : DataManagementAction

    data object NavigateUp : DataManagementAction
}

sealed interface DataManagementEvent {
    data class Exported(val counts: DataCounts) : DataManagementEvent

    data object ExportFailed : DataManagementEvent

    /** The picked file can't be imported, and why. */
    data class CantImport(val problem: BackupProblem) : DataManagementEvent

    data class Merged(val added: DataCounts) : DataManagementEvent

    data object Replaced : DataManagementEvent

    data object ImportFailed : DataManagementEvent

    data object Cleared : DataManagementEvent

    data object ClearFailed : DataManagementEvent
}
