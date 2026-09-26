package com.example.liftbook

import android.app.Application
import com.example.liftbook.notification.channel.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LiftBookApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
    }
}
