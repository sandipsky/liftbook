package com.example.liftbook.domain.usecase

import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.model.CloudSetup
import com.example.liftbook.domain.model.CloudSetupChoice
import com.example.liftbook.domain.model.CloudState
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.testing.FakeAccountRepository
import com.example.liftbook.testing.FakeBackupRepository
import com.example.liftbook.testing.FakeCloudBackupRepository
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRestTimerScheduler
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.testReminders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CloudBackupsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)
    private val onPhone = DataCounts(workouts = 142, routines = 6, weighIns = 31)
    private val sam = FakeAccountRepository.account("sam@example.com")
    private val accounts = FakeAccountRepository(sam)
    private val cloud = FakeCloudBackupRepository()
    private val backups = FakeBackupRepository(onPhone) { clock.instant() }
    private val exercises = FakeExerciseRepository()
    private val workouts = FakeWorkoutRepository(FakeRoutineRepository(exercises), exercises)

    private val online = BackupSnapshot("online", Instant.parse("2026-09-20T08:00:00Z"), DataCounts(workouts = 128, routines = 5, weighIns = 28))
    private val onlineInfo = CloudBackupInfo(online.exportedAt, online.counts)

    /** Started, as in the app, so changes are followed; the collectors run on the test scheduler. */
    private fun TestScope.cloudBackups(): CloudBackups =
        CloudBackups(accounts, cloud, backups, workouts, testReminders(clock = clock), FakeRestTimerScheduler(), clock, backgroundScope)
            .also {
                it.start()
                runCurrent()
            }

    private fun TestScope.letBackupsRun() {
        advanceTimeBy(CloudBackups.AUTO_BACKUP_DELAY_MILLIS + 1)
        runCurrent()
    }

    @Test
    fun `signing in with nothing online claims the phone's data and backs it up`() = runTest {
        val cloudBackups = cloudBackups()

        assertEquals(CloudSetup.Done, cloudBackups.prepare())
        assertEquals(CloudState(ownerAccountId = sam.id, backupPending = true), backups.cloudState.value)

        letBackupsRun()
        assertEquals(listOf(sam.id), cloud.uploads.map { it.first })
        assertEquals(onPhone, cloud.stored.getValue(sam.id).counts)
        assertEquals(CloudState(ownerAccountId = sam.id, lastBackupAt = clock.instant(), backupPending = false), backups.cloudState.value)
    }

    @Test
    fun `an empty phone with nothing online is claimed, with nothing to back up`() = runTest {
        backups.counts.value = DataCounts()
        val cloudBackups = cloudBackups()

        assertEquals(CloudSetup.Done, cloudBackups.prepare())
        letBackupsRun()

        assertEquals(CloudState(ownerAccountId = sam.id, backupPending = false), backups.cloudState.value)
        assertTrue(cloud.uploads.isEmpty())
    }

    @Test
    fun `with data on both sides the user chooses, and the backup replaces the phone on request`() = runTest {
        cloud.stored[sam.id] = online
        val cloudBackups = cloudBackups()

        assertEquals(CloudSetup.ChooseSource(onPhone, onlineInfo), cloudBackups.prepare())
        assertNull(backups.cloudState.value.ownerAccountId)

        assertEquals(online.counts, cloudBackups.resolve(CloudSetupChoice.USE_BACKUP))
        assertEquals(listOf(online to ImportMode.REPLACE), backups.restores)
        assertEquals(online.counts, backups.counts.value)
        assertEquals(CloudState(ownerAccountId = sam.id, lastBackupAt = online.exportedAt, backupPending = false), backups.cloudState.value)
        letBackupsRun()
        assertTrue(cloud.uploads.isEmpty())
    }

    @Test
    fun `keeping the phone's data backs it up over what was online`() = runTest {
        cloud.stored[sam.id] = online
        val cloudBackups = cloudBackups()
        cloudBackups.prepare()

        assertNull(cloudBackups.resolve(CloudSetupChoice.KEEP_PHONE))

        assertEquals(CloudState(ownerAccountId = sam.id, backupPending = true), backups.cloudState.value)
        letBackupsRun()
        assertEquals(onPhone, cloud.stored.getValue(sam.id).counts)
        assertTrue(backups.restores.isEmpty())
    }

    @Test
    fun `another account's data is replaced only on request`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = "uid-other@example.com")
        cloud.stored[sam.id] = online
        val cloudBackups = cloudBackups()

        assertEquals(CloudSetup.OtherAccountData(onPhone, onlineInfo), cloudBackups.prepare())
        assertEquals("uid-other@example.com", backups.cloudState.value.ownerAccountId)

        assertEquals(online.counts, cloudBackups.resolve(CloudSetupChoice.USE_BACKUP))
        assertEquals(online.counts, backups.counts.value)
        assertEquals(sam.id, backups.cloudState.value.ownerAccountId)
    }

    @Test
    fun `another account's data goes even when this account has no backup yet`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = "uid-other@example.com")
        val cloudBackups = cloudBackups()

        assertEquals(CloudSetup.OtherAccountData(onPhone, null), cloudBackups.prepare())
        assertEquals(DataCounts(), cloudBackups.resolve(CloudSetupChoice.USE_BACKUP))

        assertEquals(1, backups.clears)
        assertEquals(CloudState(ownerAccountId = sam.id, backupPending = false), backups.cloudState.value)
    }

    @Test
    fun `a change marks a backup pending, which goes up once no workout is in progress`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id, lastBackupAt = Instant.parse("2026-09-25T18:00:00Z"))
        val cloudBackups = cloudBackups()
        workouts.startEmpty("Evening workout")

        backups.change()
        advanceTimeBy(CloudBackups.CHANGE_SETTLE_MILLIS + 1)
        runCurrent()
        assertTrue(backups.cloudState.value.backupPending)

        letBackupsRun()
        assertTrue(cloud.uploads.isEmpty())
        assertTrue(cloudBackups.status.first().isWaitingForWorkout)

        workouts.active.value = null
        letBackupsRun()
        assertEquals(1, cloud.uploads.size)
        assertFalse(backups.cloudState.value.backupPending)
        assertEquals(clock.instant(), backups.cloudState.value.lastBackupAt)
        assertFalse(cloudBackups.status.first().isWaitingForWorkout)
    }

    @Test
    fun `with automatic backup off, a pending backup waits for Back up now`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id, backupPending = true, autoBackup = false)
        val cloudBackups = cloudBackups()

        letBackupsRun()
        assertTrue(cloud.uploads.isEmpty())
        assertFalse(cloudBackups.status.first().autoBackup)

        cloudBackups.backUpNow()
        assertEquals(1, cloud.uploads.size)
        assertFalse(backups.cloudState.value.backupPending)
    }

    @Test
    fun `a backup that fails is reported, and retried later`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id, backupPending = true)
        cloud.failure = AccountException(AccountProblem.OFFLINE)
        val cloudBackups = cloudBackups()

        letBackupsRun()
        assertTrue(cloud.uploads.isEmpty())
        assertEquals(AccountProblem.OFFLINE, cloudBackups.status.first().lastFailure)
        assertTrue(backups.cloudState.value.backupPending)

        advanceTimeBy(CloudBackups.RETRY_DELAY_MILLIS + 1)
        runCurrent()
        assertEquals(1, cloud.uploads.size)
        assertNull(cloudBackups.status.first().lastFailure)
        assertFalse(backups.cloudState.value.backupPending)
    }

    @Test
    fun `restoring by hand replaces the phone with the backup`() = runTest {
        backups.cloudState.value = CloudState(ownerAccountId = sam.id)
        cloud.stored[sam.id] = online
        val cloudBackups = cloudBackups()

        assertEquals(online.counts, cloudBackups.restore())

        assertEquals(listOf(online to ImportMode.REPLACE), backups.restores)
        assertEquals(online.exportedAt, backups.cloudState.value.lastBackupAt)
    }

    @Test
    fun `backing up by hand needs the phone's data to be the account's`() = runTest {
        val cloudBackups = cloudBackups()

        val problem = try {
            cloudBackups.backUpNow()
            null
        } catch (e: AccountException) {
            e.problem
        }

        assertEquals(AccountProblem.NOT_SET_UP, problem)
        assertTrue(cloud.uploads.isEmpty())
    }
}
