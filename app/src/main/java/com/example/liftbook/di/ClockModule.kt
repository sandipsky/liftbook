package com.example.liftbook.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Singleton

/** The app's single source of "now", so date-sensitive logic is testable with a fixed clock. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /**
     * Where weeks start: the locale's convention until FR-6.2 adds the setting. Not a singleton,
     * so a change of locale applies to the next screen that asks.
     */
    @Provides
    fun provideWeekFields(): WeekFields = WeekFields.of(Locale.getDefault())
}
