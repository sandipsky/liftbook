package com.example.liftbook.ui.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.core.time.ticks
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** What the banner shows about the workout in progress. */
data class ActiveWorkoutBannerState(
    val name: String,
    val startedAt: Instant,
    val rest: RestTimer?,
)

/**
 * Feeds the banner docked above the bottom bar (architecture §4.2): while a workout is in
 * progress, every top-level screen shows it — how long it's run, or the rest left — one tap
 * from resuming.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveWorkoutBannerViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {

    val activeWorkout: StateFlow<ActiveWorkoutBannerState?> = workoutRepository.observeActiveWorkout()
        .map { workout -> workout?.let { ActiveWorkoutBannerState(it.name, it.startedAt, it.rest) } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Now, once a second while there's a workout to show the time of; still otherwise. */
    val now: StateFlow<Instant> = activeWorkout
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { active -> if (active) clock.ticks() else flowOf(clock.instant()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), clock.instant())
}
