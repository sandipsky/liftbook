package com.example.liftbook.testing

import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.repository.CloudBackupRepository

/** One backup per account, in memory. Records every upload, and can be made to fail. */
class FakeCloudBackupRepository : CloudBackupRepository {

    /** What each account holds. */
    val stored = mutableMapOf<String, BackupSnapshot>()

    /** Every upload, in order, with the account it went to. */
    val uploads = mutableListOf<Pair<String, BackupSnapshot>>()

    /** When set, the next call throws it. */
    var failure: AccountException? = null

    override suspend fun describe(accountId: String): CloudBackupInfo? {
        fail()
        return stored[accountId]?.let { CloudBackupInfo(it.exportedAt, it.counts) }
    }

    override suspend fun upload(accountId: String, snapshot: BackupSnapshot) {
        fail()
        stored[accountId] = snapshot
        uploads += accountId to snapshot
    }

    override suspend fun download(accountId: String): BackupSnapshot? {
        fail()
        return stored[accountId]
    }

    private fun fail() {
        failure?.let {
            failure = null
            throw it
        }
    }
}
