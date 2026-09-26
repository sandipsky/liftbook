package com.example.liftbook.di

import com.example.liftbook.data.backup.BackupDocuments
import com.example.liftbook.data.backup.ContentResolverBackupDocuments
import com.example.liftbook.data.repository.BackupRepositoryImpl
import com.example.liftbook.data.repository.BodyWeightRepositoryImpl
import com.example.liftbook.data.repository.ExerciseRepositoryImpl
import com.example.liftbook.data.repository.ProgressRepositoryImpl
import com.example.liftbook.data.repository.RoutineRepositoryImpl
import com.example.liftbook.data.repository.ScheduleRepositoryImpl
import com.example.liftbook.data.repository.SettingsRepositoryImpl
import com.example.liftbook.data.repository.WorkoutRepositoryImpl
import com.example.liftbook.domain.repository.BackupRepository
import com.example.liftbook.domain.repository.BodyWeightRepository
import com.example.liftbook.domain.repository.ExerciseRepository
import com.example.liftbook.domain.repository.ProgressRepository
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    abstract fun bindBodyWeightRepository(impl: BodyWeightRepositoryImpl): BodyWeightRepository

    @Binds
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    abstract fun bindBackupDocuments(impl: ContentResolverBackupDocuments): BackupDocuments

    @Binds
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository
}
