package com.example.liftbook.notification.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.domain.model.ReminderNotice
import com.example.liftbook.notification.channel.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** What the reminder says, and what it lets the user do from it (FR-7.2–7.4). */
@RunWith(RobolectricTestRunner::class)
class SystemReminderNotifierTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val notifier = SystemReminderNotifier(application, Clock.fixed(Instant.parse("2026-09-26T17:50:00Z"), ZoneOffset.UTC))
    private val notice = ReminderNotice(
        scheduleId = "sat",
        startsAt = Instant.parse("2026-09-26T18:00:00Z"),
        minutesToStart = 10,
        routineId = "push",
        routineName = "Push",
        exerciseNames = listOf("Bench Press", "Overhead Press"),
        snoozeMinutes = 10,
    )

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.createAll(application)
    }

    private fun posted(): Notification = manager.activeNotifications.single { it.tag == "sat" }.notification

    @Test
    fun `a reminder names the workout, when it starts, and what's in it`() {
        notifier.show(notice)

        val notification = posted()
        assertEquals(NotificationChannels.WORKOUT_REMINDERS, notification.channelId)
        assertTrue(notification.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Push at "))
        assertEquals("Starts in 10 minutes", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(
            "Starts in 10 minutes\nBench Press · Overhead Press",
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
        )
        assertEquals(notice.startsAt.toEpochMilli(), notification.`when`)
    }

    @Test
    fun `it offers Start now, Snooze and Skip today`() {
        notifier.show(notice)

        assertEquals(listOf("Start now", "Snooze 10 min", "Skip today"), posted().actions.map { it.title.toString() })
    }

    @Test
    fun `a workout with no routine is a plain one, starting now once its time has come`() {
        notifier.show(notice.copy(routineId = null, routineName = null, exerciseNames = emptyList(), minutesToStart = 0))

        val notification = posted()
        assertTrue(notification.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Workout at "))
        assertEquals("Time to start", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `each entry has one reminder, and it can be taken down`() {
        notifier.show(notice)
        notifier.show(notice.copy(minutesToStart = 0))
        notifier.show(notice.copy(scheduleId = "wed"))
        assertEquals(2, manager.activeNotifications.size)

        notifier.dismiss("sat")

        assertEquals(listOf("wed"), manager.activeNotifications.map { it.tag })
    }
}
