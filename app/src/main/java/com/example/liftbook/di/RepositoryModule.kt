package com.example.liftbook.di

import com.example.liftbook.data.repository.ExerciseRepositoryImpl
import com.example.liftbook.data.repository.RoutineRepositoryImpl
import com.example.liftbook.data.repository.SettingsRepositoryImpl
import com.example.liftbook.data.repository.WorkoutRepositoryImpl
import com.example.liftbook.domain.repository.ExerciseRepository
import com.example.liftbook.domain.repository.RoutineRepository
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
}
