package com.example.liftbook.domain.repository

/**
 * Books a pending cloud backup with the system, to be sent once the phone is online — even if
 * the app has been closed by then (FR-8.5). `CloudBackups` sends backups itself while the app
 * is running; this covers the backup marked pending offline, or just before the app went away,
 * so it goes up when a connection returns rather than at the next launch.
 */
interface BackupUploadScheduler {

    /** Asks for the pending backup to be sent as soon as there's a connection. One already waiting is kept. */
    fun scheduleWhenOnline()

    /** Withdraws a waiting request: the backup went up another way. */
    fun cancel()
}
