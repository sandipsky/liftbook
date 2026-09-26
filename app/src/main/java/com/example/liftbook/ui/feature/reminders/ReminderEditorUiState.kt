package com.example.liftbook.ui.feature.reminders

import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.UserPreferences
import java.time.DayOfWeek
import java.time.LocalTime

data class ReminderEditorUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    /** The entry being edited no longer exists. */
    val isUnavailable: Boolean = false,
    val days: Set<DayOfWeek> = emptySet(),
    val startTime: LocalTime = ReminderEditorViewModel.DEFAULT_START,
    /** The linked routine; null for none, or once it has been deleted. */
    val routine: RoutineOption? = null,
    /** The entry's own lead time; null follows [defaultLeadMinutes]. */
    val leadMinutes: Int? = null,
    val defaultLeadMinutes: Int = UserPreferences.DEFAULT_REMINDER_LEAD_MINUTES,
    /** Every routine, by name, for the picker. */
    val routines: List<RoutineOption> = emptyList(),
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.MONDAY,
    /** Save was tapped with no day picked. */
    val showDaysError: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)

data class RoutineOption(val id: String, val name: String)

sealed interface ReminderEditorAction {
    data class ToggleDay(val day: DayOfWeek) : ReminderEditorAction

    data class SetStartTime(val time: LocalTime) : ReminderEditorAction

    data class SetRoutine(val routineId: String?) : ReminderEditorAction

    /** Null follows the default. */
    data class SetLead(val minutes: Int?) : ReminderEditorAction

    data object Save : ReminderEditorAction

    /** Confirmed already. */
    data object Delete : ReminderEditorAction

    // Navigation; handled by the route.
    data object Close : ReminderEditorAction
}

sealed interface ReminderEditorEvent {
    data object Saved : ReminderEditorEvent

    data object Deleted : ReminderEditorEvent

    data object SaveFailed : ReminderEditorEvent
}
