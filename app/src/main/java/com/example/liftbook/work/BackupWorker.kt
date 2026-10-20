package com.example.liftbook.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.liftbook.domain.usecase.CloudBackups
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The system's turn at a pending cloud backup (FR-8.5): run by WorkManager once the phone has a
 * connection, whether or not the app is open. The use case decides whether anything is due and
 * does the sending; this only asks for another go when an upload actually failed.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cloudBackups: CloudBackups,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (cloudBackups.backUpIfDue()) Result.success() else Result.retry()
}
