package com.example.liftbook.ui.feature.reminders

import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WorkoutSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReminderListUiState(
    val isLoading: Boolean = true,
    /** The global switch (FR-7.7). */
    val remindersEnabled: Boolean = true,
    /** Earliest in the day first. */
    val schedules: List<ScheduleItem> = emptyList(),
    /** The next workout that will be reminded; null with reminders off or nothing on. */
    val next: NextWorkout? = null,
    /** Workouts a week across the entries that are on. */
    val perWeek: Int = 0,
    val defaultLeadMinutes: Int = UserPreferences.DEFAULT_REMINDER_LEAD_MINUTES,
    val snoozeMinutes: Int = UserPreferences.DEFAULT_SNOOZE_MINUTES,
    /** Where the week starts (FR-6.2), for the order days are listed in. */
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.MONDAY,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Whether anything will actually remind: the moment to ask for notifications (FR-7.6). */
    val remindsAnything: Boolean get() = remindersEnabled && schedules.any { it.schedule.isEnabled }
}

data class ScheduleItem(
    val schedule: WorkoutSchedule,
    /** Today is one of its days and its workout was skipped from the reminder (FR-7.4). */
    val isSkippedToday: Boolean = false,
)

data class NextWorkout(val startsAt: Instant, val routineName: String?)

sealed interface ReminderListAction {
    data class SetRemindersEnabled(val enabled: Boolean) : ReminderListAction

    data class SetScheduleEnabled(val scheduleId: String, val enabled: Boolean) : ReminderListAction

    data class SetDefaultLead(val minutes: Int) : ReminderListAction

    data class SetSnooze(val minutes: Int) : ReminderListAction

    // Navigation; handled by the route.
    data class OpenSchedule(val scheduleId: String) : ReminderListAction

    data object AddSchedule : ReminderListAction

    data object NavigateUp : ReminderListAction
}

sealed interface ReminderListEvent {
    data object SaveFailed : ReminderListEvent
}
