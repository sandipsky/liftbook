package com.example.liftbook.domain.model

import java.time.Instant

/**
 * A backup held in memory rather than in a file: the document itself, as a file export writes
 * it (FR-6.3), and what it holds. It's what goes to and comes from the cloud (FR-8.5).
 */
data class BackupSnapshot(
    val document: String,
    val exportedAt: Instant,
    val counts: DataCounts,
)

/** What an account holds online, without the data itself (FR-8.5). */
data class CloudBackupInfo(
    val savedAt: Instant,
    val counts: DataCounts,
)

/**
 * This phone's relation to the cloud (FR-8.5, FR-8.6): whose data it holds, when that data last
 * went up, and whether it has changed since.
 */
data class CloudState(
    /** The account the data on this phone belongs to; null while it belongs to no account. */
    val ownerAccountId: String? = null,
    val lastBackupAt: Instant? = null,
    /** Something changed since the last backup, or there has never been one of this data. */
    val backupPending: Boolean = false,
    /** Whether a backup follows every change (FR-8.7). Off, only Back up now sends one. */
    val autoBackup: Boolean = true,
)

/** The cloud backup as a screen shows it: the stored state, plus what's happening right now. */
data class CloudBackupStatus(
    val lastBackupAt: Instant? = null,
    val isPending: Boolean = false,
    val isBackingUp: Boolean = false,
    /** Why the last attempt failed; null once one succeeds. */
    val lastFailure: AccountProblem? = null,
    /** A backup is due, but waits for the workout in progress to finish. */
    val isWaitingForWorkout: Boolean = false,
    val autoBackup: Boolean = true,
)

/** What signing in found on this phone and online, when the two don't simply agree (FR-8.6). */
sealed interface CloudSetup {
    /** Nothing to decide: this phone's data is the account's now. */
    data object Done : CloudSetup

    /** The account has a backup, and this phone's data belongs to no account yet. One has to give way. */
    data class ChooseSource(val phone: DataCounts, val backup: CloudBackupInfo) : CloudSetup

    /** This phone holds another account's data. It goes before this account's comes in. */
    data class OtherAccountData(val phone: DataCounts, val backup: CloudBackupInfo?) : CloudSetup
}

enum class CloudSetupChoice {
    /** Replace what's on this phone with the account's backup — or with nothing, if it has none. */
    USE_BACKUP,

    /** Keep this phone's data and back it up, over whatever the account held. */
    KEEP_PHONE,
}
