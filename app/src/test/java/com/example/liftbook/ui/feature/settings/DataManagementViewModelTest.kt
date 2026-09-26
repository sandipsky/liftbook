package com.example.liftbook.ui.feature.settings

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.BackupProblem
import com.example.liftbook.domain.model.BackupSummary
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.testing.FakeBackupRepository
import com.example.liftbook.testing.FakeRestTimerScheduler
import com.example.liftbook.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DataManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val onPhone = DataCounts(workouts = 142, routines = 6, customExercises = 4, weighIns = 31)
    private val backups = FakeBackupRepository(onPhone)
    private val restTimer = FakeRestTimerScheduler()
    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)

    private val file = DocumentUri("content://downloads/backup.json")
    private val summary = BackupSummary(Instant.parse("2026-09-12T08:00:00Z"), DataCounts(workouts = 128, routines = 5, weighIns = 28))
    private val events = mutableListOf<DataManagementEvent>()

    init {
        backups.backups[file] = summary
    }

    private fun TestScope.dataManagement(): DataManagementViewModel =
        DataManagementViewModel(backups, restTimer, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        }

    private fun restTimerRunning() = restTimer.schedule(Instant.parse("2026-09-26T10:01:30Z"), "Bench Press")

    @Test
    fun `shows what's on the phone and when it was last backed up`() = runTest {
        backups.lastExportedAt.value = Instant.parse("2026-09-20T08:00:00Z")

        val state = dataManagement().uiState.value

        assertFalse(state.isLoading)
        assertEquals(onPhone, state.counts)
        assertEquals(Instant.parse("2026-09-20T08:00:00Z"), state.lastExportedAt)
        assertEquals("liftbook-backup-2026-09-26.json", state.backupFileName)
    }

    @Test
    fun `an export reports what it saved, and when it was made`() = runTest {
        val viewModel = dataManagement()

        viewModel.onAction(DataManagementAction.ExportTo(file))

        assertEquals(listOf(file), backups.exports)
        assertEquals(listOf<DataManagementEvent>(DataManagementEvent.Exported(onPhone)), events)
        assertNotNull(viewModel.uiState.value.lastExportedAt)
        assertNull(viewModel.uiState.value.operation)
    }

    @Test
    fun `an export that fails says so`() = runTest {
        backups.failure = IOException("Disk full")

        dataManagement().onAction(DataManagementAction.ExportTo(file))

        assertEquals(listOf<DataManagementEvent>(DataManagementEvent.ExportFailed), events)
    }

    @Test
    fun `a backup is read before anything is imported, and merge is offered first`() = runTest {
        val viewModel = dataManagement()

        viewModel.onAction(DataManagementAction.ReadBackup(file))

        assertEquals(ImportDraft(file, summary, ImportMode.MERGE), viewModel.uiState.value.importing)
        assertTrue(backups.imports.isEmpty())
    }

    @Test
    fun `on an empty phone, replace is offered first`() = runTest {
        backups.counts.value = DataCounts()
        val viewModel = dataManagement()

        viewModel.onAction(DataManagementAction.ReadBackup(file))

        assertEquals(ImportMode.REPLACE, viewModel.uiState.value.importing?.mode)
    }

    @Test
    fun `a merge runs at once and says what it added`() = runTest {
        backups.added[file] = DataCounts(workouts = 12, weighIns = 3)
        val viewModel = dataManagement()
        viewModel.onAction(DataManagementAction.ReadBackup(file))

        viewModel.onAction(DataManagementAction.StartImport)

        assertEquals(listOf(file to ImportMode.MERGE), backups.imports)
        assertEquals(listOf<DataManagementEvent>(DataManagementEvent.Merged(DataCounts(workouts = 12, weighIns = 3))), events)
        assertNull(viewModel.uiState.value.importing)
    }

    @Test
    fun `a replace asks first, and only then replaces and stops the rest alert`() = runTest {
        restTimerRunning()
        val viewModel = dataManagement()
        viewModel.onAction(DataManagementAction.ReadBackup(file))
        viewModel.onAction(DataManagementAction.ChooseImportMode(ImportMode.REPLACE))

        viewModel.onAction(DataManagementAction.StartImport)

        assertTrue(viewModel.uiState.value.isConfirmingReplace)
        assertTrue(backups.imports.isEmpty())

        viewModel.onAction(DataManagementAction.ConfirmReplace)

        assertEquals(listOf(file to ImportMode.REPLACE), backups.imports)
        assertEquals(listOf<DataManagementEvent>(DataManagementEvent.Replaced), events)
        assertFalse(viewModel.uiState.value.isConfirmingReplace)
        assertNull(viewModel.uiState.value.importing)
        // The workout in progress went with the phone's data, and its rest with it.
        assertNull(restTimer.scheduledAt)
    }

    @Test
    fun `backing out of a replace keeps the backup open to merge instead`() = runTest {
        val viewModel = dataManagement()
        viewModel.onAction(DataManagementAction.ReadBackup(file))
        viewModel.onAction(DataManagementAction.ChooseImportMode(ImportMode.REPLACE))
        viewModel.onAction(DataManagementAction.StartImport)

        viewModel.onAction(DataManagementAction.DismissReplace)

        assertFalse(viewModel.uiState.value.isConfirmingReplace)
        assertNotNull(viewModel.uiState.value.importing)
        assertTrue(backups.imports.isEmpty())
    }

    @Test
    fun `a file that can't be imported says why, and nothing opens`() = runTest {
        backups.failure = BackupException(BackupProblem.NEWER_VERSION)
        val viewModel = dataManagement()

        viewModel.onAction(DataManagementAction.ReadBackup(file))

        assertNull(viewModel.uiState.value.importing)
        assertEquals(listOf<DataManagementEvent>(DataManagementEvent.CantImport(BackupProblem.NEWER_VERSION)), events)
    }

    @Test
    fun `an import that fails closes the sheet and says nothing changed`() = runTest {
        val viewModel = dataManagement()
        viewModel.onAction(DataManagementAction.ReadBackup(file))
        backups.failure = IllegalStateException("Constraint failed")

        viewModel.onAction(DataManagementAction.StartImport)

        assertNull(viewModel.uiState.value.importing)
        assertEquals(listOf<DataManagementEvent>(DataManagementEvent.ImportFailed), events)
    }

    @Test
    fun `clearing asks for the word each time, then clears and stops the rest alert`() = runTest {
        restTimerRunning()
        val viewModel = dataManagement()
        viewModel.clearConfirmation.setTextAndPlaceCursorAtEnd("delete")

        viewModel.onAction(DataManagementAction.ClearAll)

        assertTrue(viewModel.uiState.value.isConfirmingClear)
        // What was typed last time doesn't count this time.
        assertEquals("", viewModel.clearConfirmation.text.toString())
        assertEquals(0, backups.clears)

        viewModel.onAction(DataManagementAction.ConfirmClear)

        assertEquals(1, backups.clears)
        assertFalse(viewModel.uiState.value.isConfirmingClear)
        assertEquals(DataCounts(), viewModel.uiState.value.counts)
        assertEquals(listOf<DataManagementEvent>(DataManagementEvent.Cleared), events)
        assertNull(restTimer.scheduledAt)
    }

    @Test
    fun `one thing at a time`() = runTest {
        val gate = CompletableDeferred<Unit>()
        backups.exportGate = gate
        val viewModel = dataManagement()

        viewModel.onAction(DataManagementAction.ExportTo(file))
        assertEquals(BackupOperation.EXPORTING, viewModel.uiState.value.operation)
        viewModel.onAction(DataManagementAction.ClearAll)
        viewModel.onAction(DataManagementAction.ReadBackup(file))

        assertFalse(viewModel.uiState.value.isConfirmingClear)
        assertNull(viewModel.uiState.value.importing)

        gate.complete(Unit)

        assertNull(viewModel.uiState.value.operation)
        assertEquals(listOf(file), backups.exports)
    }
}
