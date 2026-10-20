package com.example.liftbook.di

import android.content.Context
import com.example.liftbook.data.cloud.FirebaseAccountRepository
import com.example.liftbook.data.cloud.FirebaseAvailability
import com.example.liftbook.data.cloud.FirestoreBackupRepository
import com.example.liftbook.data.cloud.UnavailableAccountRepository
import com.example.liftbook.data.cloud.UnavailableCloudBackupRepository
import com.example.liftbook.domain.repository.AccountRepository
import com.example.liftbook.domain.repository.BackupRepository
import com.example.liftbook.domain.repository.BackupUploadScheduler
import com.example.liftbook.domain.repository.CloudBackupRepository
import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.domain.usecase.CloudBackups
import com.example.liftbook.domain.usecase.WorkoutReminders
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Work that outlives any screen, such as a backup following a change; lives as long as the process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The account and the cloud backup (FR-8). Both come from Firebase when the build has its
 * configuration, and from stand-ins that say so when it doesn't, so the app never needs to know.
 */
@Module
@InstallIn(SingletonComponent::class)
object CloudModule {

    @Provides
    @Singleton
    fun provideAccountRepository(@ApplicationContext context: Context): AccountRepository =
        if (FirebaseAvailability.isConfigured(context)) FirebaseAccountRepository(FirebaseAuth.getInstance()) else UnavailableAccountRepository

    @Provides
    @Singleton
    fun provideCloudBackupRepository(@ApplicationContext context: Context): CloudBackupRepository =
        if (FirebaseAvailability.isConfigured(context)) FirestoreBackupRepository(FirestoreBackupRepository.firestore()) else UnavailableCloudBackupRepository

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** One instance, so its lock covers every backup, restore and set-up. */
    @Provides
    @Singleton
    fun provideCloudBackups(
        accounts: AccountRepository,
        cloud: CloudBackupRepository,
        backups: BackupRepository,
        workouts: WorkoutRepository,
        reminders: WorkoutReminders,
        restTimer: RestTimerScheduler,
        uploads: BackupUploadScheduler,
        clock: Clock,
        @ApplicationScope scope: CoroutineScope,
    ): CloudBackups = CloudBackups(accounts, cloud, backups, workouts, reminders, restTimer, uploads, clock, scope)
}
