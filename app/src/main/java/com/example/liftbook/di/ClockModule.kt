package com.example.liftbook.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * The app's single source of "now", so date-sensitive logic is testable with a fixed clock.
 * Where weeks start is a setting (FR-6.2), read from SettingsRepository.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
