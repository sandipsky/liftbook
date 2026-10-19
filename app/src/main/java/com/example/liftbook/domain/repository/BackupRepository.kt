package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.BackupSummary
import com.example.liftbook.domain.model.CloudState
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.ImportMode
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * How data leaves or arrives on this phone: a versioned JSON backup, written to and read from a
 * file the user picks (FR-6.3, FR-6.4, NFR-6) or held in memory for the cloud (FR-8.5), and
 * clearing it all (FR-6.5). The cloud's bookkeeping lives here too: whose data this phone holds.
 */
interface BackupRepository {

    /** What's on this phone, counted as a backup counts it. */
    fun observeCounts(): Flow<DataCounts>

    /** When a backup was last exported from this phone; null if never, or since data was cleared. */
    val lastExportedAt: Flow<Instant?>

    /**
     * Writes every exercise, routine, finished workout and weigh-in, and the settings, to
     * [destination] as one file (FR-6.3). Returns what it holds.
     */
    suspend fun export(destination: DocumentUri): DataCounts

    /** Reads [source] without importing anything. Throws [BackupException] when it can't be imported. */
    suspend fun inspect(source: DocumentUri): BackupSummary

    /**
     * Imports [source] (FR-6.4), all of it or none. Returns what it added: for [ImportMode.REPLACE],
     * everything in the backup. Throws [BackupException] when it can't be imported.
     */
    suspend fun import(source: DocumentUri, mode: ImportMode): DataCounts

    /** Deletes all data and resets settings, leaving LiftBook as it was when installed (FR-6.5). */
    suspend fun clearAll()

    /** Everything on this phone as one backup, held in memory for the cloud copy (FR-8.5). Not counted as an export. */
    suspend fun snapshot(): BackupSnapshot

    /** Imports a backup held in memory, as [import] does a file. Throws [BackupException] when it can't be. */
    suspend fun restore(snapshot: BackupSnapshot, mode: ImportMode): DataCounts

    /** Emits whenever anything a backup holds changes, so the cloud copy can follow (FR-8.5). Nothing on subscribing. */
    fun observeChanges(): Flow<Unit>

    /** Whose data this phone holds, when it last went up, and whether it has changed since (FR-8.6). */
    val cloudState: Flow<CloudState>

    suspend fun setCloudOwner(accountId: String?)

    suspend fun setCloudBackupPending(pending: Boolean)

    /** Whether backups follow changes on their own (FR-8.7). */
    suspend fun setCloudAutoBackup(enabled: Boolean)

    /** A backup of this phone's data went up: it's current as of [at]. */
    suspend fun setCloudBackedUp(at: Instant)
}
