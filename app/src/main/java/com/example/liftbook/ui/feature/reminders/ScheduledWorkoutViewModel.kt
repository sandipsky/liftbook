package com.example.liftbook.ui.feature.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.ReminderLaunch
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.domain.usecase.WorkoutReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * A reminder that opened the app (FR-7.3, FR-7.4). Tapping it offers the workout with a one-tap
 * start, over whatever the app was showing; Start now starts it straight away. Scoped to the
 * activity, which hands it the reminder; what's on offer survives the process being killed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduledWorkoutViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val reminders: WorkoutReminders,
    private val clock: Clock,
) : ViewModel() {

    /** The workout on offer; null when nothing is. */
    private val offer = MutableStateFlow(restoreOffer())

    private val progress = MutableStateFlow(Progress())

    private val _events = Channel<ScheduledWorkoutEvent>(Channel.BUFFERED)
    val events: Flow<ScheduledWorkoutEvent> = _events.receiveAsFlow()

    val prompt: StateFlow<ScheduledWorkoutPrompt?> = offer
        .flatMapLatest<Offer?, Pair<Offer, Routine?>?> { offer ->
            when {
                offer == null -> flowOf(null)
                offer.routineId == null -> flowOf(offer to null)
                else -> routineRepository.observeRoutine(offer.routineId).map { routine -> offer to routine }
            }
        }
        .combine(progress) { offered, progress ->
            offered?.let { (offer, routine) ->
                ScheduledWorkoutPrompt(
                    startsAt = offer.startsAt,
                    routineName = routine?.name,
                    exerciseNames = routine?.exercises?.map { it.exercise.name }.orEmpty(),
                    isStarting = progress.isStarting,
                    startFailed = progress.failed,
                    today = LocalDate.now(clock),
                    zone = clock.zone,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Answers [launch]. [emptyWorkoutName] names the workout if it has no routine to start from,
     * resolved by the caller in the user's language.
     */
    fun onLaunch(launch: ReminderLaunch, emptyWorkoutName: String) {
        reminders.dismiss(launch.scheduleId)
        val offer = Offer(launch.startsAt, launch.routineId, emptyWorkoutName)
        progress.value = Progress()
        if (launch.startNow) {
            start(offer)
        } else {
            show(offer)
        }
    }

    fun onAction(action: ScheduledWorkoutAction) {
        when (action) {
            ScheduledWorkoutAction.Start -> offer.value?.let(::start)
            ScheduledWorkoutAction.Dismiss -> if (!progress.value.isStarting) show(null)
        }
    }

    private fun start(offer: Offer) {
        if (progress.value.isStarting) return
        progress.value = Progress(isStarting = true)
        viewModelScope.launch {
            val result = withContext(NonCancellable) {
                try {
                    if (offer.routineId != null) {
                        workoutRepository.startFromRoutine(offer.routineId)
                    } else {
                        workoutRepository.startEmpty(offer.emptyWorkoutName)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            when (result) {
                is StartWorkoutResult.Started, is StartWorkoutResult.Resumed -> {
                    show(null)
                    _events.send(ScheduledWorkoutEvent.OpenWorkout)
                }
                is StartWorkoutResult.OtherWorkoutActive -> {
                    show(null)
                    _events.send(ScheduledWorkoutEvent.OtherWorkoutActive(result.name))
                }
                // The routine is gone: offer the workout without it, for the user to start or not.
                StartWorkoutResult.RoutineNotFound -> {
                    progress.value = Progress()
                    show(offer.copy(routineId = null))
                }
                null -> {
                    progress.value = Progress(failed = true)
                    show(offer)
                }
            }
        }
    }

    private fun show(offer: Offer?) {
        this.offer.value = offer
        if (offer == null) progress.update { Progress() }
        savedStateHandle[KEY_STARTS_AT] = offer?.startsAt?.toEpochMilli()
        savedStateHandle[KEY_ROUTINE_ID] = offer?.routineId
        savedStateHandle[KEY_EMPTY_NAME] = offer?.emptyWorkoutName
    }

    private fun restoreOffer(): Offer? {
        val startsAt = savedStateHandle.get<Long>(KEY_STARTS_AT) ?: return null
        val name = savedStateHandle.get<String>(KEY_EMPTY_NAME) ?: return null
        return Offer(Instant.ofEpochMilli(startsAt), savedStateHandle[KEY_ROUTINE_ID], name)
    }

    private data class Offer(val startsAt: Instant, val routineId: String?, val emptyWorkoutName: String)

    private data class Progress(val isStarting: Boolean = false, val failed: Boolean = false)

    private companion object {
        const val KEY_STARTS_AT = "scheduledStartsAt"
        const val KEY_ROUTINE_ID = "scheduledRoutineId"
        const val KEY_EMPTY_NAME = "scheduledEmptyName"
    }
}
