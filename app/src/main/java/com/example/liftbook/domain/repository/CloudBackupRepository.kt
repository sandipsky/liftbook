package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.CloudBackupInfo

/**
 * An account's own copy of a backup online, under its id and nobody else's (FR-8.5). One per
 * account: an upload replaces what was there. Every call throws [AccountException] when it
 * can't reach the server or isn't allowed to.
 */
interface CloudBackupRepository {

    /** What's stored for [accountId], or null if nothing yet. */
    suspend fun describe(accountId: String): CloudBackupInfo?

    suspend fun upload(accountId: String, snapshot: BackupSnapshot)

    /** The whole backup, or null if the account has none. */
    suspend fun download(accountId: String): BackupSnapshot?
}
