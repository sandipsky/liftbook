package com.example.liftbook.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.liftbook.domain.repository.BackupUploadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * [BackupUploadScheduler] on WorkManager: one named job, kept if already waiting, that runs
 * [BackupWorker] when the phone is connected and backs off between failed attempts. The system
 * may hold it until a good moment on phones that throttle background work; worst case it runs
 * when the app next opens, which is what happened before there was a job at all.
 */
class WorkManagerBackupUploadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BackupUploadScheduler {

    override fun scheduleWhenOnline() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "cloud-backup"
        const val BACKOFF_SECONDS = 30L
    }
}
