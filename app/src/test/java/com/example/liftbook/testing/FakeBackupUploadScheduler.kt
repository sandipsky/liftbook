package com.example.liftbook.testing

import com.example.liftbook.domain.repository.BackupUploadScheduler

/** Remembers whether a backup is booked with the system, and how often it was asked. */
class FakeBackupUploadScheduler : BackupUploadScheduler {

    /** A job is waiting for a connection. */
    var isWaiting = false
        private set

    var requests = 0
        private set

    override fun scheduleWhenOnline() {
        requests++
        isWaiting = true
    }

    override fun cancel() {
        isWaiting = false
    }
}
