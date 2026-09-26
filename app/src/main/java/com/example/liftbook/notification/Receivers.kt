package com.example.liftbook.notification

import android.content.BroadcastReceiver
import android.content.Context
import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.domain.usecase.WorkoutReminders
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * What the broadcast receivers need from the app's graph. Receivers are looked up rather than
 * injected: Hilt's receiver injection needs a `super.onReceive` call Kotlin can't make.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun workoutReminders(): WorkoutReminders

    fun workoutRepository(): WorkoutRepository

    fun restTimerScheduler(): RestTimerScheduler

    fun clock(): Clock
}

internal fun Context.receiverEntryPoint(): ReceiverEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReceiverEntryPoint::class.java)

/**
 * Runs [work] off the main thread, keeping the receiver alive until it's done — well within the
 * seconds a broadcast gets. A failure is dropped rather than crashing the app in the background:
 * the next sync, at the latest when the app opens, puts things right.
 */
internal fun BroadcastReceiver.runAsync(work: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to show from a receiver; see above.
        } finally {
            pending.finish()
        }
    }
}
