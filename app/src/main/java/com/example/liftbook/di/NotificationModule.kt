package com.example.liftbook.di

import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.notification.rest.AlarmRestTimerScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    abstract fun bindRestTimerScheduler(impl: AlarmRestTimerScheduler): RestTimerScheduler
}
