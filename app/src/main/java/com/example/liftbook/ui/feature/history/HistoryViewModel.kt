package com.example.liftbook.ui.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.liftbook.domain.calculator.byDay
import com.example.liftbook.domain.calculator.instantsIn
import com.example.liftbook.domain.calculator.totals
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Every finished workout (FR-4.1), as a list grouped by month or as a calendar of training days
 * (FR-4.4). Which one shows, and the calendar's month, survive the process being killed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Paged, with a heading at the start of each month: history grows without bound (NFR-2). */
    val workouts: Flow<PagingData<HistoryListItem>> = workoutRepository.observeFinishedWorkouts()
        .map { page -> page.withMonthHeadings(clock.zone) }
        .cachedIn(viewModelScope)

    private val view: Flow<HistoryView> = savedStateHandle.getStateFlow(KEY_VIEW, HistoryView.List.name).map(HistoryView::valueOf)

    private val month: Flow<YearMonth> = savedStateHandle.getStateFlow(KEY_MONTH, YearMonth.now(clock).toString()).map(YearMonth::parse)

    /** The calendar's month, queried only while the calendar shows. */
    private val calendar: Flow<CalendarMonth?> = view.flatMapLatest { view ->
        if (view != HistoryView.Calendar) {
            flowOf(null)
        } else {
            month.flatMapLatest { month ->
                val span = month.instantsIn(clock.zone)
                workoutRepository.observeFinishedBetween(span.from, span.until).map { workouts ->
                    CalendarMonth(
                        month = month,
                        days = workouts.byDay(clock.zone),
                        totals = workouts.totals(),
                        hasNext = month < YearMonth.now(clock),
                    )
                }
            }
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(view, calendar, settingsRepository.userPreferences) { view, calendar, preferences ->
        HistoryUiState(
            view = view,
            calendar = calendar,
            weightUnit = preferences.weightUnit,
            firstDayOfWeek = preferences.firstDayOfWeek.dayOfWeek,
            today = LocalDate.now(clock),
            zone = clock.zone,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryUiState(today = LocalDate.now(clock), zone = clock.zone),
    )

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.ShowView -> savedStateHandle[KEY_VIEW] = action.view.name
            HistoryAction.PreviousMonth -> moveMonth(-1)
            HistoryAction.NextMonth -> moveMonth(1)
            // Navigation; handled by the route.
            is HistoryAction.OpenWorkout, HistoryAction.StartWorkout -> Unit
        }
    }

    /** The calendar goes back as far as wanted, but never past the current month. */
    private fun moveMonth(months: Long) {
        val current = savedStateHandle.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: YearMonth.now(clock)
        val next = current.plusMonths(months)
        if (next <= YearMonth.now(clock)) savedStateHandle[KEY_MONTH] = next.toString()
    }

    private companion object {
        const val KEY_VIEW = "view"
        const val KEY_MONTH = "month"
    }
}

/**
 * The month whose heading goes between two neighbouring workouts of the newest-first list, or
 * null when they share one. The first workout always starts a month.
 */
internal fun monthHeadingBetween(before: WorkoutListItem?, after: WorkoutListItem?, zone: ZoneId): YearMonth? {
    val next = after ?: return null
    val month = YearMonth.from(next.startedAt.atZone(zone))
    return month.takeIf { before == null || YearMonth.from(before.startedAt.atZone(zone)) != it }
}

private fun PagingData<WorkoutListItem>.withMonthHeadings(zone: ZoneId): PagingData<HistoryListItem> =
    map<WorkoutListItem, HistoryListItem> { HistoryListItem.Workout(it) }
        .insertSeparators { before, after ->
            monthHeadingBetween(
                (before as? HistoryListItem.Workout)?.workout,
                (after as? HistoryListItem.Workout)?.workout,
                zone,
            )?.let(HistoryListItem::Month)
        }
