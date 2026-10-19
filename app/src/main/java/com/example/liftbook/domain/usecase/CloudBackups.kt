package com.example.liftbook.domain.usecase

import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.model.CloudBackupStatus
import com.example.liftbook.domain.model.CloudSetup
import com.example.liftbook.domain.model.CloudSetupChoice
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.domain.repository.AccountRepository
import com.example.liftbook.domain.repository.BackupRepository
import com.example.liftbook.domain.repository.CloudBackupRepository
import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.domain.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cloud backup (FR-8.5, FR-8.6): the signed-in account's own copy of this phone's data.
 *
 * The phone is the source of truth and the cloud holds a backup of it — the same document a
 * file export writes (FR-6.3), under the account's id and nobody else's. The phone's data
 * belongs to one account at a time, the one that last backed it up or restored it, and
 * [prepare] settles whose it is when an account signs in. From then on a backup follows every
 * change, once the changes have settled and no workout is in progress — unless automatic backup
 * is switched off (FR-8.7) — and one can be taken or restored by hand.
 *
 * One instance, [start]ed when the app's UI comes up: its lock keeps a backup, a restore and
 * the set-up from running over each other.
 */
class CloudBackups(
    private val accounts: AccountRepository,
    private val cloud: CloudBackupRepository,
    private val backups: BackupRepository,
    private val workouts: WorkoutRepository,
    private val reminders: WorkoutReminders,
    private val restTimer: RestTimerScheduler,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val activity = MutableStateFlow(Activity())
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    /** A restore changes the database too; those changes aren't ones to back up. */
    @Volatile
    private var ignoreChangesUntil: Instant = Instant.MIN

    val status: Flow<CloudBackupStatus> = combine(backups.cloudState, activity, workouts.observeActiveWorkout()) { state, activity, active ->
        CloudBackupStatus(
            lastBackupAt = state.lastBackupAt,
            isPending = state.backupPending,
            isBackingUp = activity.isBackingUp,
            lastFailure = activity.lastFailure,
            isWaitingForWorkout = state.backupPending && state.autoBackup && active != null,
            autoBackup = state.autoBackup,
        )
    }

    /**
     * Keeps the cloud copy following the phone. Any change marks a backup pending once it has
     * settled; a pending backup goes up as soon as the owner is signed in and no workout is in
     * progress, and is retried if it fails. Once per process; later calls do nothing.
     */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            backups.observeChanges().debounce(CHANGE_SETTLE_MILLIS).collect {
                if (clock.instant().isBefore(ignoreChangesUntil)) return@collect
                if (!backups.cloudState.first().backupPending) backups.setCloudBackupPending(true)
            }
        }
        scope.launch {
            combine(accounts.account, backups.cloudState, workouts.observeActiveWorkout()) { account, state, active ->
                account?.id?.takeIf { state.ownerAccountId == it && state.backupPending && state.autoBackup && active == null }
            }.distinctUntilChanged().collectLatest { accountId ->
                if (accountId == null) return@collectLatest
                delay(AUTO_BACKUP_DELAY_MILLIS)
                while (true) {
                    // Once started, a backup finishes: a workout beginning mid-upload mustn't leave half of one.
                    val done = withContext(NonCancellable) {
                        try {
                            mutex.withLock { backUp(accountId) }
                            true
                        } catch (e: AccountException) {
                            false
                        }
                    }
                    if (done) return@collectLatest
                    delay(RETRY_DELAY_MILLIS)
                }
            }
        }
    }

    /** Backs up now, for the signed-in account. Throws [AccountException] if it can't. */
    suspend fun backUpNow() {
        val account = signedIn()
        mutex.withLock {
            if (backups.cloudState.first().ownerAccountId != account.id) throw AccountException(AccountProblem.NOT_SET_UP)
            backUp(account.id)
        }
    }

    /** What the signed-in account holds online, or null if nothing yet. */
    suspend fun describe(): CloudBackupInfo? = cloud.describe(signedIn().id)

    /** Replaces this phone's data with the account's backup (FR-8.5). Returns what came in. */
    suspend fun restore(): DataCounts {
        val account = signedIn()
        return mutex.withLock {
            val snapshot = cloud.download(account.id) ?: throw AccountException(AccountProblem.NO_BACKUP)
            replaceWith(snapshot, account.id)
        }
    }

    /**
     * Settles whose data this phone holds, after signing in (FR-8.6). Nothing to decide when it's
     * already the account's, or when neither side has anything the other lacks; otherwise the
     * choice is the user's, answered with [resolve].
     */
    suspend fun prepare(): CloudSetup {
        val account = signedIn()
        return mutex.withLock {
            val state = backups.cloudState.first()
            if (state.ownerAccountId == account.id) return CloudSetup.Done
            val phone = backups.observeCounts().first()
            val backup = cloud.describe(account.id)
            when {
                state.ownerAccountId != null -> CloudSetup.OtherAccountData(phone, backup)
                backup != null -> CloudSetup.ChooseSource(phone, backup)
                else -> {
                    claim(account.id, pending = !phone.isEmpty)
                    CloudSetup.Done
                }
            }
        }
    }

    /** Answers [prepare]'s question. Returns what came down when the backup was used; null when the phone's data stayed. */
    suspend fun resolve(choice: CloudSetupChoice): DataCounts? {
        val account = signedIn()
        return mutex.withLock {
            when (choice) {
                CloudSetupChoice.USE_BACKUP -> {
                    val snapshot = cloud.download(account.id)
                    if (snapshot != null) {
                        replaceWith(snapshot, account.id)
                    } else {
                        ignoreChanges()
                        backups.clearAll()
                        afterReplace()
                        claim(account.id, pending = false)
                        DataCounts()
                    }
                }
                CloudSetupChoice.KEEP_PHONE -> {
                    claim(account.id, pending = !backups.observeCounts().first().isEmpty)
                    null
                }
            }
        }
    }

    private suspend fun backUp(accountId: String) {
        activity.update { it.copy(isBackingUp = true) }
        try {
            val snapshot = backups.snapshot()
            cloud.upload(accountId, snapshot)
            backups.setCloudBackedUp(snapshot.exportedAt)
            activity.update { it.copy(isBackingUp = false, lastFailure = null) }
        } catch (e: CancellationException) {
            activity.update { it.copy(isBackingUp = false) }
            throw e
        } catch (e: AccountException) {
            activity.update { it.copy(isBackingUp = false, lastFailure = e.problem) }
            throw e
        } catch (e: Exception) {
            activity.update { it.copy(isBackingUp = false, lastFailure = AccountProblem.UNKNOWN) }
            throw AccountException(AccountProblem.UNKNOWN, e)
        }
    }

    private suspend fun replaceWith(snapshot: BackupSnapshot, accountId: String): DataCounts {
        ignoreChanges()
        val counts = backups.restore(snapshot, ImportMode.REPLACE)
        afterReplace()
        backups.setCloudOwner(accountId)
        backups.setCloudBackedUp(snapshot.exportedAt)
        return counts
    }

    private fun ignoreChanges() {
        ignoreChangesUntil = clock.instant().plusMillis(IGNORE_OWN_CHANGES_MILLIS)
    }

    /** A replace takes the workout in progress with it, and the schedule changes with the rest (FR-7.6). */
    private suspend fun afterReplace() {
        restTimer.cancel()
        try {
            reminders.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The data is in; the alarm follows at the latest when the app next opens.
        }
    }

    private suspend fun claim(accountId: String, pending: Boolean) {
        backups.setCloudOwner(accountId)
        backups.setCloudBackupPending(pending)
    }

    private suspend fun signedIn(): Account = accounts.account.first() ?: throw AccountException(AccountProblem.SIGNED_OUT)

    private data class Activity(
        val isBackingUp: Boolean = false,
        val lastFailure: AccountProblem? = null,
    )

    companion object {
        /** How long after the last change before it counts as settled. */
        const val CHANGE_SETTLE_MILLIS = 5_000L

        /** A pause before an automatic backup, so a run of saves goes up as one. */
        const val AUTO_BACKUP_DELAY_MILLIS = 10_000L

        const val RETRY_DELAY_MILLIS = 5 * 60_000L

        /** Longer than the settle time, so a restore's own writes have come and gone. */
        const val IGNORE_OWN_CHANGES_MILLIS = CHANGE_SETTLE_MILLIS * 3
    }
}
