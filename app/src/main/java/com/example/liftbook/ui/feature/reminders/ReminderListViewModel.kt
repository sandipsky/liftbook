package com.example.liftbook.ui.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.ReminderTimes
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.usecase.WorkoutReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The weekly schedule and the reminder settings (FR-7.1, FR-7.2, FR-7.4, FR-7.7). Everything
 * applies as it's changed, and every change brings the reminder alarm in step (FR-7.6).
 */
@HiltViewModel
class ReminderListViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val reminders: WorkoutReminders,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ReminderListUiState> = combine(
        scheduleRepository.observeSchedules(),
        settingsRepository.userPreferences,
    ) { schedules, preferences ->
        val today = LocalDate.now(clock)
        val next = if (preferences.remindersEnabled) ReminderTimes.nextStart(schedules, clock.instant(), clock.zone) else null
        ReminderListUiState(
            isLoading = false,
            remindersEnabled = preferences.remindersEnabled,
            schedules = schedules.map { schedule ->
                ScheduleItem(schedule, isSkippedToday = schedule.skippedOn == today && today.dayOfWeek in schedule.days)
            },
            next = next?.let { (schedule, startsAt) -> NextWorkout(startsAt, schedule.routineName) },
            perWeek = ReminderTimes.perWeek(schedules),
            defaultLeadMinutes = preferences.reminderLeadMinutes,
            snoozeMinutes = preferences.snoozeMinutes,
            firstDayOfWeek = preferences.firstDayOfWeek,
            today = today,
            zone = clock.zone,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReminderListUiState(today = LocalDate.now(clock), zone = clock.zone),
    )

    private val _events = Channel<ReminderListEvent>(Channel.BUFFERED)
    val events: Flow<ReminderListEvent> = _events.receiveAsFlow()

    fun onAction(action: ReminderListAction) {
        when (action) {
            is ReminderListAction.SetRemindersEnabled -> save { settingsRepository.setRemindersEnabled(action.enabled) }
            is ReminderListAction.SetScheduleEnabled -> save { scheduleRepository.setEnabled(action.scheduleId, action.enabled) }
            is ReminderListAction.SetDefaultLead -> save { settingsRepository.setReminderLeadMinutes(action.minutes) }
            is ReminderListAction.SetSnooze -> save { settingsRepository.setSnoozeMinutes(action.minutes) }
            // Navigation; handled by the route.
            is ReminderListAction.OpenSchedule, ReminderListAction.AddSchedule, ReminderListAction.NavigateUp -> Unit
        }
    }

    /** Writes, then sets the alarm to match — both, even if the screen closes in between. */
    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch {
            val saved = withContext(NonCancellable) {
                try {
                    write()
                    reminders.sync()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
            if (!saved) _events.send(ReminderListEvent.SaveFailed)
        }
    }
}
