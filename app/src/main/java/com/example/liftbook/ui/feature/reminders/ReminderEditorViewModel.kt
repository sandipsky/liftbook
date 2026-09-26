package com.example.liftbook.ui.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.ScheduleDraft
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.ScheduleRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.usecase.WorkoutReminders
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * Schedules a workout (FR-7.1): its days, when it starts, the routine it's for, and when to be
 * reminded (FR-7.2). Nothing is written until Save, so leaving discards the edit as a whole, as
 * the routine editor does. Saving turns the entry on, and brings the alarm in step (FR-7.6).
 */
@HiltViewModel(assistedFactory = ReminderEditorViewModel.Factory::class)
class ReminderEditorViewModel @AssistedInject constructor(
    /** Null to schedule a new workout. */
    @Assisted private val scheduleId: String?,
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
    settingsRepository: SettingsRepository,
    private val reminders: WorkoutReminders,
) : ViewModel() {

    private val form = MutableStateFlow(
        if (scheduleId == null) Form(saved = NEW_ENTRY, draft = NEW_ENTRY) else Form(isLoading = true),
    )

    private val _events = Channel<ReminderEditorEvent>(Channel.BUFFERED)
    val events: Flow<ReminderEditorEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ReminderEditorUiState> = combine(
        form,
        routineRepository.observeRoutines(),
        settingsRepository.userPreferences,
    ) { form, routines, preferences ->
        val options = routines.map { RoutineOption(it.id, it.name) }
        ReminderEditorUiState(
            isEditing = scheduleId != null,
            isLoading = form.isLoading,
            isUnavailable = form.isUnavailable,
            days = form.draft.days,
            startTime = form.draft.startTime,
            // A routine deleted meanwhile reads as none, as the schedule itself will once the link is cleared.
            routine = form.draft.routineId?.let { id -> options.firstOrNull { it.id == id } },
            leadMinutes = form.draft.leadMinutes,
            defaultLeadMinutes = preferences.reminderLeadMinutes,
            routines = options,
            firstDayOfWeek = preferences.firstDayOfWeek,
            showDaysError = form.submitted && form.draft.days.isEmpty(),
            isSaving = form.isSaving,
            hasUnsavedChanges = !form.isLoading && form.draft != form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderEditorUiState(isEditing = scheduleId != null, isLoading = scheduleId != null))

    init {
        if (scheduleId != null) {
            viewModelScope.launch {
                val schedule = scheduleRepository.observeSchedule(scheduleId).first()
                form.update {
                    if (schedule == null) {
                        it.copy(isLoading = false, isUnavailable = true)
                    } else {
                        val loaded = ScheduleDraft(schedule.days, schedule.startTime, schedule.routineId, schedule.leadMinutes)
                        it.copy(isLoading = false, saved = loaded, draft = loaded)
                    }
                }
            }
        }
    }

    fun onAction(action: ReminderEditorAction) {
        when (action) {
            is ReminderEditorAction.ToggleDay -> edit { draft ->
                draft.copy(days = if (action.day in draft.days) draft.days - action.day else draft.days + action.day)
            }
            is ReminderEditorAction.SetStartTime -> edit { it.copy(startTime = action.time.withSecond(0).withNano(0)) }
            is ReminderEditorAction.SetRoutine -> edit { it.copy(routineId = action.routineId) }
            is ReminderEditorAction.SetLead -> edit { it.copy(leadMinutes = action.minutes) }
            ReminderEditorAction.Save -> save()
            ReminderEditorAction.Delete -> delete()
            // Navigation; handled by the route.
            ReminderEditorAction.Close -> Unit
        }
    }

    private fun edit(change: (ScheduleDraft) -> ScheduleDraft) = form.update { if (it.isLoading) it else it.copy(draft = change(it.draft)) }

    private fun save() {
        val current = form.value
        if (current.isLoading || current.isSaving) return
        if (!current.draft.isValid) {
            form.update { it.copy(submitted = true) }
            return
        }
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = withContext(NonCancellable) {
                try {
                    // Link only a routine that still exists: one deleted meanwhile would fail the save.
                    val routineId = current.draft.routineId?.takeIf { routineRepository.getRoutine(it) != null }
                    val draft = current.draft.copy(routineId = routineId)
                    if (scheduleId == null) scheduleRepository.create(draft) else scheduleRepository.update(scheduleId, draft)
                    reminders.sync()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
            if (saved) {
                form.update { it.copy(saved = it.draft) }
                _events.send(ReminderEditorEvent.Saved)
            } else {
                form.update { it.copy(isSaving = false) }
                _events.send(ReminderEditorEvent.SaveFailed)
            }
        }
    }

    private fun delete() {
        val id = scheduleId ?: return
        if (form.value.isSaving) return
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val deleted = withContext(NonCancellable) {
                try {
                    scheduleRepository.delete(id)
                    reminders.dismiss(id)
                    reminders.sync()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            }
            if (deleted) {
                _events.send(ReminderEditorEvent.Deleted)
            } else {
                form.update { it.copy(isSaving = false) }
                _events.send(ReminderEditorEvent.SaveFailed)
            }
        }
    }

    private data class Form(
        val isLoading: Boolean = false,
        val isUnavailable: Boolean = false,
        /** What's stored, to tell whether leaving loses anything. */
        val saved: ScheduleDraft = NEW_ENTRY,
        val draft: ScheduleDraft = NEW_ENTRY,
        val submitted: Boolean = false,
        val isSaving: Boolean = false,
    )

    @AssistedFactory
    interface Factory {
        fun create(scheduleId: String?): ReminderEditorViewModel
    }

    companion object {
        /** An evening, when most people train after work; changed with one tap. */
        val DEFAULT_START: LocalTime = LocalTime.of(18, 0)

        private val NEW_ENTRY = ScheduleDraft(days = emptySet(), startTime = DEFAULT_START)
    }
}
