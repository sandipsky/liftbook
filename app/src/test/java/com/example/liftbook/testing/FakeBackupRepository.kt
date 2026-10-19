package com.example.liftbook.testing

import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.BackupSummary
import com.example.liftbook.domain.model.CloudState
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.domain.repository.BackupRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Backups by URI, in memory. Records what was asked of it, and can be made to fail or wait. */
class FakeBackupRepository(
    counts: DataCounts = DataCounts(),
    private val now: () -> Instant = { Instant.parse("2026-09-26T10:00:00Z") },
) : BackupRepository {

    val counts = MutableStateFlow(counts)
    override val lastExportedAt = MutableStateFlow<Instant?>(null)
    override val cloudState = MutableStateFlow(CloudState())

    /** Backups that can be read, by where they are. */
    val backups = mutableMapOf<DocumentUri, BackupSummary>()

    /** What a merge of each backup adds. */
    val added = mutableMapOf<DocumentUri, DataCounts>()

    val exports = mutableListOf<DocumentUri>()
    val imports = mutableListOf<Pair<DocumentUri, ImportMode>>()
    val restores = mutableListOf<Pair<BackupSnapshot, ImportMode>>()
    var clears = 0
        private set

    /** When set, the next call throws it. */
    var failure: Exception? = null

    /** When set, export waits for it — to see what the screen does meanwhile. */
    var exportGate: CompletableDeferred<Unit>? = null

    private val changes = MutableSharedFlow<Unit>()

    override fun observeCounts() = counts

    override suspend fun export(destination: DocumentUri): DataCounts {
        exportGate?.await()
        fail()
        exports += destination
        lastExportedAt.value = now()
        return counts.value
    }

    override suspend fun inspect(source: DocumentUri): BackupSummary {
        fail()
        return backups.getValue(source)
    }

    override suspend fun import(source: DocumentUri, mode: ImportMode): DataCounts {
        fail()
        imports += source to mode
        val backup = backups.getValue(source)
        return when (mode) {
            ImportMode.MERGE -> added[source] ?: backup.counts
            ImportMode.REPLACE -> backup.counts.also { counts.value = it }
        }
    }

    override suspend fun clearAll() {
        fail()
        clears++
        counts.value = DataCounts()
        lastExportedAt.value = null
        cloudState.value = CloudState()
    }

    /** The document stands in for the JSON; what matters is that it comes back as it went. */
    override suspend fun snapshot(): BackupSnapshot {
        fail()
        return BackupSnapshot(document = "snapshot:${counts.value}", exportedAt = now(), counts = counts.value)
    }

    override suspend fun restore(snapshot: BackupSnapshot, mode: ImportMode): DataCounts {
        fail()
        restores += snapshot to mode
        if (mode == ImportMode.REPLACE) counts.value = snapshot.counts
        return snapshot.counts
    }

    override fun observeChanges(): Flow<Unit> = changes

    /** Something a backup holds changed. */
    suspend fun change() = changes.emit(Unit)

    override suspend fun setCloudOwner(accountId: String?) = cloudState.update { it.copy(ownerAccountId = accountId) }

    override suspend fun setCloudBackupPending(pending: Boolean) = cloudState.update { it.copy(backupPending = pending) }

    override suspend fun setCloudAutoBackup(enabled: Boolean) = cloudState.update { it.copy(autoBackup = enabled) }

    override suspend fun setCloudBackedUp(at: Instant) = cloudState.update { it.copy(lastBackupAt = at, backupPending = false) }

    private fun fail() {
        failure?.let {
            failure = null
            throw it
        }
    }
}
