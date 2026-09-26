package com.example.liftbook.ui.feature.history

import com.example.liftbook.domain.calculator.WorkoutTotals
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.WorkoutListItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** How the history is shown: every workout, newest first (FR-4.1), or a month at a time (FR-4.4). */
enum class HistoryView { List, Calendar }

data class HistoryUiState(
    val view: HistoryView = HistoryView.List,
    /** The calendar's month; null until its workouts have loaded, and while the list shows. */
    val calendar: CalendarMonth? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** Where the calendar's weeks start (FR-6.2). */
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    /** The zone workouts are placed on a day in. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** A month of the calendar with its workouts (FR-4.4). */
data class CalendarMonth(
    val month: YearMonth,
    /** By the day they started on, each day's oldest first. */
    val days: Map<LocalDate, List<WorkoutListItem>>,
    val totals: WorkoutTotals,
    /** A later month exists to go to: the calendar never goes past this one. */
    val hasNext: Boolean,
)

/** A row of the history list: a month's heading, or a workout in it. */
sealed interface HistoryListItem {
    val key: String

    data class Month(val month: YearMonth) : HistoryListItem {
        override val key: String get() = "month-$month"
    }

    data class Workout(val workout: WorkoutListItem) : HistoryListItem {
        override val key: String get() = workout.id
    }
}

sealed interface HistoryAction {
    data class ShowView(val view: HistoryView) : HistoryAction

    data object PreviousMonth : HistoryAction

    data object NextMonth : HistoryAction

    // Navigation; handled by the route.
    data class OpenWorkout(val workoutId: String) : HistoryAction

    /** From the empty history: go to where a workout starts. */
    data object StartWorkout : HistoryAction
}
