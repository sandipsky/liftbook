package com.example.liftbook.notification.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the rest timer's alarm when the rest is over; shows the alert (FR-3.5). */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        RestTimerNotification.show(context, intent.getStringExtra(EXTRA_NEXT_EXERCISE))
    }

    companion object {
        const val EXTRA_NEXT_EXERCISE = "com.example.liftbook.extra.NEXT_EXERCISE"
    }
}
