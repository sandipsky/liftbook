package com.example.liftbook.di

import com.example.liftbook.domain.repository.BackupUploadScheduler
import com.example.liftbook.work.WorkManagerBackupUploadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Background work: the cloud backup that waits for a connection (FR-8.5). */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    abstract fun bindBackupUploadScheduler(impl: WorkManagerBackupUploadScheduler): BackupUploadScheduler
}
