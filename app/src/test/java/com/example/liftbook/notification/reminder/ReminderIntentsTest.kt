package com.example.liftbook.notification.reminder

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.domain.model.ReminderLaunch
import com.example.liftbook.domain.model.ReminderNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** What a reminder's taps carry into the app (FR-7.3, FR-7.4). */
@RunWith(RobolectricTestRunner::class)
class ReminderIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notice = ReminderNotice(
        scheduleId = "sat",
        startsAt = Instant.parse("2026-09-26T18:00:00Z"),
        minutesToStart = 10,
        routineId = "push",
        routineName = "Push",
        exerciseNames = listOf("Bench Press"),
        snoozeMinutes = 10,
    )

    @Test
    fun `tapping the reminder opens it, and Start now starts it`() {
        assertEquals(
            ReminderLaunch("sat", "push", notice.startsAt, startNow = false),
            ReminderIntents.parse(ReminderIntents.open(context, notice, start = false)),
        )
        assertEquals(
            ReminderLaunch("sat", "push", notice.startsAt, startNow = true),
            ReminderIntents.parse(ReminderIntents.open(context, notice, start = true)),
        )
    }

    @Test
    fun `a reminder reaches the app that's already open, rather than a second copy of it`() {
        val flags = ReminderIntents.open(context, notice, start = false).flags

        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun `an entry with no routine opens with none`() {
        val launch = ReminderIntents.parse(ReminderIntents.open(context, notice.copy(routineId = null), start = true))

        assertNull(launch?.routineId)
    }

    @Test
    fun `an ordinary launch, or one missing its entry, isn't a reminder`() {
        assertNull(ReminderIntents.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(ReminderIntents.parse(Intent(ReminderIntents.ACTION_OPEN)))
    }
}
