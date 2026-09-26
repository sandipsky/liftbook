package com.example.liftbook.notification.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.liftbook.data.preferences.SettingsKeys
import com.example.liftbook.domain.repository.ReminderAlarm
import com.example.liftbook.notification.setWakeUpAlarm
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one reminder alarm (FR-7.6), `setExactAndAllowWhileIdle` when allowed so it fires in Doze
 * and after the app is killed. When it's set for is written down too: AlarmManager can't be
 * asked, and knowing lets a sync deliver an alarm that's due but hasn't gone off yet.
 */
@Singleton
class AlarmReminderAlarm @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : ReminderAlarm {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override suspend fun armedAt(): Instant? = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .first()[SettingsKeys.reminderAlarmAt]
        ?.let(Instant::ofEpochMilli)

    override suspend fun arm(at: Instant) {
        dataStore.edit { it[SettingsKeys.reminderAlarmAt] = at.toEpochMilli() }
        alarmManager?.setWakeUpAlarm(at, operation())
    }

    override suspend fun disarm() {
        alarmManager?.cancel(operation())
        dataStore.edit { it.remove(SettingsKeys.reminderAlarmAt) }
    }

    /** The same target and request code every time, so each alarm replaces the last. */
    private fun operation(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 2
    }
}
