package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.BackupSummary
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.ImportMode
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The only way data leaves or arrives on this phone: a versioned JSON backup, written to and
 * read from a file the user picks (FR-6.3, FR-6.4, NFR-6), and clearing it all (FR-6.5).
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
}
