package com.example.liftbook.ui.feature.reminders

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.ui.components.AlertIssue
import com.example.liftbook.ui.theme.LiftBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The reminders screen, driven through its semantics as TalkBack or a thumb would. */
@RunWith(RobolectricTestRunner::class)
class ReminderListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<ReminderListAction>()
    private var fixed: AlertIssue? = null

    private val weekdays = WorkoutSchedule(
        id = "weekdays",
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        startTime = LocalTime.of(7, 0),
        routineName = "Push",
    )

    private fun show(state: ReminderListUiState, alertIssue: AlertIssue? = null) {
        compose.setContent {
            LiftBookTheme {
                ReminderListScreen(
                    state = state,
                    alertIssue = alertIssue,
                    snackbarHostState = remember { SnackbarHostState() },
                    onFixAlerts = { fixed = it },
                    onAction = { actions += it },
                )
            }
        }
    }

    private fun state(vararg schedules: WorkoutSchedule, remindersEnabled: Boolean = true) = ReminderListUiState(
        isLoading = false,
        remindersEnabled = remindersEnabled,
        schedules = schedules.map { ScheduleItem(it) },
        firstDayOfWeek = FirstDayOfWeek.MONDAY,
        today = LocalDate.of(2026, 9, 26),
        zone = ZoneOffset.UTC,
    )

    @Test
    fun `with nothing scheduled, the empty state leads to scheduling one`() {
        show(state())

        compose.onNodeWithText("No workouts scheduled").assertExists()
        compose.onNodeWithText("Schedule a workout").performClick()

        assertEquals(listOf<ReminderListAction>(ReminderListAction.AddSchedule), actions)
    }

    @Test
    fun `the global switch is one target, and says what it will do`() {
        show(state(weekdays, remindersEnabled = false))

        compose.onNodeWithText("Remind me before workouts").assertIsOff()
        compose.onNodeWithText("No reminders are sent").assertExists()
        compose.onNodeWithText("Remind me before workouts").performClick()

        assertEquals(listOf<ReminderListAction>(ReminderListAction.SetRemindersEnabled(true)), actions)
    }

    @Test
    fun `each entry is heard in words, opens to edit, and has its own switch`() {
        show(state(weekdays))

        // "Mon · Wed · Fri" on screen; the days in full to TalkBack.
        val card = compose.onNode(hasClickAction() and hasContentDescription("Monday, Wednesday, Friday, Push", substring = true))
        val entrySwitch = compose.onNodeWithContentDescription("Reminder", substring = true)
        entrySwitch.assertIsOn()
        entrySwitch.performClick()
        card.performClick()

        assertEquals(
            listOf(ReminderListAction.SetScheduleEnabled("weekdays", enabled = false), ReminderListAction.OpenSchedule("weekdays")),
            actions,
        )
    }

    @Test
    fun `what keeps reminders from arriving is said, with the way to fix it`() {
        show(state(weekdays), alertIssue = AlertIssue.NotificationsOff)

        compose.onNodeWithText("Notifications are off, so reminders can’t reach you.").assertExists()
        compose.onNodeWithText("Turn on").performClick()

        assertEquals(AlertIssue.NotificationsOff, fixed)
        assertTrue(actions.isEmpty())
    }
}
