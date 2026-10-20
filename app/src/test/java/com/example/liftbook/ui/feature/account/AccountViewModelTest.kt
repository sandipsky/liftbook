package com.example.liftbook.ui.feature.account

import androidx.lifecycle.SavedStateHandle
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.model.CloudSetup
import com.example.liftbook.domain.model.CloudSetupChoice
import com.example.liftbook.domain.model.CloudState
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.usecase.CloudBackups
import com.example.liftbook.testing.FakeAccountRepository
import com.example.liftbook.testing.FakeBackupRepository
import com.example.liftbook.testing.FakeBackupUploadScheduler
import com.example.liftbook.testing.FakeCloudBackupRepository
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRestTimerScheduler
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.testReminders
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)
    private val onPhone = DataCounts(workouts = 142, routines = 6, weighIns = 31)
    private val sam = FakeAccountRepository.account()
    private val accounts = FakeAccountRepository()
    private val cloud = FakeCloudBackupRepository()
    private val backups = FakeBackupRepository(onPhone) { clock.instant() }
    private val exercises = FakeExerciseRepository()
    private val workouts = FakeWorkoutRepository(FakeRoutineRepository(exercises), exercises)
    private val savedStateHandle = SavedStateHandle()
    private val events = mutableListOf<AccountEvent>()

    private val online = BackupSnapshot("online", Instant.parse("2026-09-20T08:00:00Z"), DataCounts(workouts = 128, routines = 5, weighIns = 28))
    private val onlineInfo = CloudBackupInfo(online.exportedAt, online.counts)

    private fun TestScope.account(): AccountViewModel {
        val cloudBackups = CloudBackups(
            accounts, cloud, backups, workouts, testReminders(clock = clock), FakeRestTimerScheduler(), FakeBackupUploadScheduler(), clock, backgroundScope,
        )
        return AccountViewModel(accounts, cloudBackups, backups, savedStateHandle, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        }
    }

    @Test
    fun `signed out, there's no backup to speak of`() = runTest {
        val state = account().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.isAvailable)
        assertNull(state.account)
        assertFalse(state.isOwner)
        assertEquals(onPhone, state.phone)
    }

    @Test
    fun `signing in with nothing online claims the phone without asking`() = runTest {
        val viewModel = account()

        accounts.account.value = sam

        val state = viewModel.uiState.value
        assertEquals(sam, state.account)
        assertTrue(state.isOwner)
        assertNull(state.setup)
        assertFalse(state.needsSetup)
        assertTrue(backups.cloudState.value.backupPending)
        assertTrue(state.hasCheckedCloud)
        assertNull(state.cloud)
    }

    @Test
    fun `signing in with a backup online asks which to keep, and restores on request`() = runTest {
        cloud.stored[sam.id] = online
        val viewModel = account()

        accounts.account.value = sam

        assertEquals(CloudSetup.ChooseSource(onPhone, onlineInfo), viewModel.uiState.value.setup)
        assertFalse(viewModel.uiState.value.isOwner)
        assertEquals(onlineInfo, viewModel.uiState.value.cloud)

        viewModel.onAction(AccountAction.Choose(CloudSetupChoice.USE_BACKUP))

        assertEquals(listOf<AccountEvent>(AccountEvent.Restored(online.counts)), events)
        val state = viewModel.uiState.value
        assertTrue(state.isOwner)
        assertNull(state.setup)
        assertEquals(online.counts, state.phone)
    }

    @Test
    fun `a question put off can be picked up again`() = runTest {
        cloud.stored[sam.id] = online
        val viewModel = account()
        accounts.account.value = sam

        viewModel.onAction(AccountAction.DismissSetup)
        assertNull(viewModel.uiState.value.setup)
        assertTrue(viewModel.uiState.value.needsSetup)

        viewModel.onAction(AccountAction.FinishSetup)
        assertNotNull(viewModel.uiState.value.setup)
    }

    @Test
    fun `backing up now reports what went up`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id)
        accounts.account.value = sam
        val viewModel = account()

        viewModel.onAction(AccountAction.BackUpNow)

        assertEquals(listOf<AccountEvent>(AccountEvent.BackedUp(onPhone)), events)
        assertEquals(onPhone, cloud.stored.getValue(sam.id).counts)
        val state = viewModel.uiState.value
        assertEquals(clock.instant(), state.backup.lastBackupAt)
        assertEquals(CloudBackupInfo(clock.instant(), onPhone), state.cloud)
        assertNull(state.operation)
    }

    @Test
    fun `restoring asks first, then replaces the phone`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id)
        cloud.stored[sam.id] = online
        accounts.account.value = sam
        val viewModel = account()
        assertEquals(onlineInfo, viewModel.uiState.value.cloud)

        viewModel.onAction(AccountAction.Restore)
        assertTrue(viewModel.uiState.value.isConfirmingRestore)
        viewModel.onAction(AccountAction.ConfirmRestore)

        assertEquals(listOf<AccountEvent>(AccountEvent.Restored(online.counts)), events)
        assertFalse(viewModel.uiState.value.isConfirmingRestore)
        assertEquals(online.counts, viewModel.uiState.value.phone)
    }

    @Test
    fun `signing out keeps the phone's data`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id)
        accounts.account.value = sam
        val viewModel = account()

        viewModel.onAction(AccountAction.SignOut)
        assertTrue(viewModel.uiState.value.isConfirmingSignOut)
        viewModel.onAction(AccountAction.ConfirmSignOut)

        assertEquals(listOf<AccountEvent>(AccountEvent.SignedOut), events)
        assertNull(viewModel.uiState.value.account)
        assertEquals(onPhone, backups.counts.value)
        assertEquals(sam.id, backups.cloudState.value.ownerAccountId)
    }

    @Test
    fun `automatic backup can be switched off`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id)
        accounts.account.value = sam
        val viewModel = account()
        assertTrue(viewModel.uiState.value.backup.autoBackup)

        viewModel.onAction(AccountAction.SetAutoBackup(false))

        assertFalse(viewModel.uiState.value.backup.autoBackup)
        assertFalse(backups.cloudState.value.autoBackup)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a changed password is announced where the user lands`() = runTest {
        account()

        savedStateHandle[AccountViewModel.PASSWORD_CHANGED] = true

        assertEquals(listOf<AccountEvent>(AccountEvent.PasswordChanged), events)
        assertEquals(false, savedStateHandle.get<Boolean>(AccountViewModel.PASSWORD_CHANGED))
    }
}
