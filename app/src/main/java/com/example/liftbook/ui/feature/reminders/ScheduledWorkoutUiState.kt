package com.example.liftbook.ui.feature.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The workout a reminder was for, offered with a one-tap start (FR-7.3). */
data class ScheduledWorkoutPrompt(
    val startsAt: Instant,
    /** The routine's name; null when the entry has none, or it was deleted. */
    val routineName: String? = null,
    /** Its exercises, in order. */
    val exerciseNames: List<String> = emptyList(),
    val isStarting: Boolean = false,
    /** The last start failed; the sheet says so and offers it again. */
    val startFailed: Boolean = false,
    val today: LocalDate,
    val zone: ZoneId,
)

sealed interface ScheduledWorkoutAction {
    data object Start : ScheduledWorkoutAction

    data object Dismiss : ScheduledWorkoutAction
}

sealed interface ScheduledWorkoutEvent {
    /** The workout started, or it was already the one in progress. */
    data object OpenWorkout : ScheduledWorkoutEvent

    /** Another workout is in progress (FR-3.1), so nothing was started. */
    data class OtherWorkoutActive(val workoutName: String) : ScheduledWorkoutEvent
}
