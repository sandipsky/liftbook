package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.core.time.ticks
import com.example.liftbook.domain.calculator.RestTimes
import com.example.liftbook.domain.calculator.SetPrefill
import com.example.liftbook.domain.calculator.findSet
import com.example.liftbook.domain.calculator.nextSetAfter
import com.example.liftbook.domain.calculator.progress
import com.example.liftbook.domain.calculator.searchExercises
import com.example.liftbook.domain.model.ExerciseFilter
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutEdits
import com.example.liftbook.domain.repository.ExerciseRepository
import com.example.liftbook.domain.repository.RestTimerScheduler
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.ui.components.ExercisePickerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * The workout in progress (FR-3.1 – FR-3.10). It holds no authoritative state: the screen is a
 * projection of the active workout in the database, and every action writes straight through
 * to it (architecture §5.3). Kill the process mid-set and the next launch shows the same
 * screen (FR-3.7).
 *
 * What the user types lives in text states owned here ([SetFields], [NoteText]) and is saved
 * shortly after typing stops. Anything that depends on those values — completing a set, adding
 * one, finishing — saves them first. All writes take turns, so they land in the order made.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val restTimerScheduler: RestTimerScheduler,
    private val clock: Clock,
) : ViewModel() {

    /** The exercise picker's search field. */
    val pickerQueryState = TextFieldState()

    /**
     * Now, once a second while the screen is showing, for its clocks: elapsed time (FR-3.6) and
     * the rest countdown (FR-3.5). Kept out of [uiState] so the tick redraws only the clocks.
     */
    val now: StateFlow<Instant> = clock.ticks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), clock.instant())

    private val local = MutableStateFlow(LocalState())

    private val _events = Channel<ActiveWorkoutEvent>(Channel.BUFFERED)
    val events: Flow<ActiveWorkoutEvent> = _events.receiveAsFlow()

    // The workout and settings as last shown, for actions to work from.
    private var workout: Workout? = null
    private var preferences = UserPreferences()

    // Editable text, by set and workout-exercise id; kept in step with the workout as it changes.
    private val setFields = HashMap<String, SetFields>()
    private var fieldsUnit: WeightUnit? = null
    private val exerciseNotes = HashMap<String, NoteText>()
    private var workoutNote: NoteText? = null
    private var workoutNoteFor: String? = null

    /** Exercises whose note field has been shown; it stays, even if the note is cleared. */
    private val shownNotes = HashSet<String>()

    /** Bumped when fields come or go, so the edit watcher follows the new set of them. */
    private val fieldsVersion = MutableStateFlow(0)

    private val writes = Mutex()

    private val picker: Flow<ExercisePickerUiState?> = local
        .map { it.picked }
        .distinctUntilChanged()
        .flatMapLatest { picked ->
            if (picked == null) {
                flowOf(null)
            } else {
                combine(exerciseRepository.observeLibrary(), snapshotFlow { pickerQueryState.text.toString() }) { library, query ->
                    ExercisePickerUiState(
                        results = searchExercises(library, ExerciseFilter(query = query)),
                        selectedIds = picked,
                        query = query.trim(),
                        libraryCount = library.size,
                    )
                }
            }
        }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepository.observeActiveWorkout(),
        settingsRepository.userPreferences,
        local,
        picker,
    ) { active, preferences, local, picker ->
        // Once discarded or finished, keep the workout on screen until the screen closes,
        // instead of flashing "no workout in progress" on the way out.
        val shown = if (local.isClosing) active ?: workout else active
        workout = shown
        this.preferences = preferences
        syncFields(shown, preferences.weightUnit)
        settleOrder(shown, local)
        stateFor(shown, preferences, local, picker)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    init {
        // Saves what's typed once typing pauses (FR-3.7). Only changes are written, so a
        // restart of the watcher when fields come or go costs nothing.
        viewModelScope.launch {
            fieldsVersion
                .flatMapLatest { snapshotFlow { editableTexts() } }
                .debounce(EDIT_SAVE_DELAY_MILLIS)
                .collect { write { saveEdits() } }
        }
    }

    fun onAction(action: ActiveWorkoutAction) {
        when (action) {
            ActiveWorkoutAction.Discard -> discard()
            ActiveWorkoutAction.Finish -> finish()
            ActiveWorkoutAction.SaveNow -> write { saveEdits() }

            ActiveWorkoutAction.OpenPicker -> {
                pickerQueryState.clearText()
                local.update { it.copy(picked = emptyList()) }
            }
            ActiveWorkoutAction.ClosePicker -> local.update { it.copy(picked = null) }
            is ActiveWorkoutAction.TogglePicked -> local.update { current ->
                val picked = current.picked ?: return@update current
                current.copy(picked = if (action.exerciseId in picked) picked - action.exerciseId else picked + action.exerciseId)
            }
            ActiveWorkoutAction.AddPicked -> addPicked()
            is ActiveWorkoutAction.RemoveExercise -> write { workoutRepository.removeExercise(action.workoutExerciseId) }
            ActiveWorkoutAction.StartReordering -> local.update { current ->
                current.copy(isReordering = true, order = workout?.exercises?.map { it.id })
            }
            ActiveWorkoutAction.StopReordering -> local.update { it.copy(isReordering = false) }
            is ActiveWorkoutAction.MoveExercise -> moveExercise(action.from, action.to)
            is ActiveWorkoutAction.ShowExerciseNote ->
                local.update { it.copy(openedNotes = it.openedNotes + action.workoutExerciseId) }

            is ActiveWorkoutAction.AddSet -> write {
                saveEdits()
                workoutRepository.addSet(action.workoutExerciseId)
            }
            is ActiveWorkoutAction.RemoveSet -> write {
                workoutRepository.removeSet(action.setId)
                local.update { it.copy(missing = it.missing - action.setId) }
            }
            is ActiveWorkoutAction.ChangeSetType -> write { workoutRepository.setSetType(action.setId, action.setType) }
            is ActiveWorkoutAction.ToggleSetDone -> write { toggleDone(action.setId) }

            ActiveWorkoutAction.SkipRest -> write { changeRest { null } }
            is ActiveWorkoutAction.AdjustRest -> write { changeRest { rest -> RestTimes.adjust(rest, action.seconds, clock.instant()) } }
            is ActiveWorkoutAction.SetExerciseRest -> write {
                workoutRepository.setExerciseRest(action.workoutExerciseId, action.seconds)
            }
            is ActiveWorkoutAction.SetDefaultRest -> write { settingsRepository.setDefaultRestSeconds(action.seconds) }

            // Navigation; handled by the route.
            ActiveWorkoutAction.NavigateUp -> Unit
        }
    }

    /**
     * Marks a set done: its typed values are checked — a set can't be done without what its
     * type records (FR-3.3) — saved, carried into the next empty sets, and the rest timer
     * starts (FR-3.5). Marking a done set again undoes it.
     */
    private suspend fun toggleDone(setId: String) {
        val workout = workout?.takeIf { it.isActive } ?: return
        val (exercise, set) = workout.findSet(setId) ?: return
        if (set.isCompleted) {
            saveEdits()
            if (workoutRepository.uncompleteSet(setId)) restTimerScheduler.cancel()
            return
        }
        val fields = setFields[setId] ?: return
        val type = exercise.exercise.type
        val texts = fields.texts()
        val values = fields.values().applicableTo(type)
        if (values.metricsFor(type) == null) {
            local.update { it.copy(missing = it.missing + setId) }
            return
        }
        saveEdits()

        val now = clock.instant()
        val rest = RestTimes.start(now, RestTimes.secondsFor(exercise, preferences.defaultRestSeconds))
        val candidates = exercise.sets.map { SetPrefill.SetCandidate(it.setType, it.isCompleted, setFields[it.id]?.values() ?: it.values) }
        val carried = SetPrefill.carryForward(type, candidates, exercise.sets.indexOf(set))
            .associate { index -> exercise.sets[index].id to values }
        carried.keys.forEach { id -> setFields[id]?.fill(values) }

        workoutRepository.completeSet(setId, values, now, rest, carried)
        fields.markSaved(values, texts)
        local.update { it.copy(missing = it.missing - setId) }
        if (rest != null) {
            restTimerScheduler.schedule(rest.endsAt, workout.nextSetAfter(setId)?.exercise?.exercise?.name)
        } else {
            restTimerScheduler.cancel()
        }
    }

    /** Skips, lengthens or shortens the rest, and moves its alert to match. */
    private suspend fun changeRest(change: (RestTimer) -> RestTimer?) {
        val workout = workout?.takeIf { it.isActive } ?: return
        val rest = workout.rest ?: return
        val changed = change(rest)
        workoutRepository.setRest(workout.id, changed)
        if (changed == null) {
            restTimerScheduler.cancel()
        } else {
            // The set whose completion started this rest carries the same instant.
            val startedBy = workout.exercises.asSequence().flatMap { it.sets }.firstOrNull { it.completedAt == rest.startedAt }
            val next = startedBy?.let { workout.nextSetAfter(it.id) }
            restTimerScheduler.schedule(changed.endsAt, next?.exercise?.exercise?.name)
        }
    }

    private fun addPicked() {
        val picked = local.value.picked ?: return
        local.update { it.copy(picked = null) }
        if (picked.isEmpty()) return
        write {
            val workout = workout?.takeIf { it.isActive } ?: return@write
            saveEdits()
            workoutRepository.addExercises(workout.id, picked).firstOrNull()?.let { first ->
                _events.send(ActiveWorkoutEvent.ExercisesAdded(first))
            }
        }
    }

    /**
     * Moves an exercise while reordering. The new order shows at once and is written straight
     * away; it's shown ahead of the database until the database catches up (see [settleOrder]).
     */
    private fun moveExercise(from: Int, to: Int) {
        val workoutId = workout?.id ?: return
        val base = local.value.order ?: workout?.exercises?.map { it.id } ?: return
        if (from !in base.indices || to !in base.indices || from == to) return
        val order = base.toMutableList().apply { add(to, removeAt(from)) }
        local.update { it.copy(order = order) }
        write(onFailure = { local.update { it.copy(order = null) } }) {
            workoutRepository.reorderExercises(workoutId, order)
        }
    }

    private fun finish() {
        val workout = workout?.takeIf { it.isActive } ?: return
        if (local.value.isClosing) return
        write(onFailure = { local.update { it.copy(isClosing = false) } }) {
            saveEdits()
            local.update { it.copy(isClosing = true) }
            if (workoutRepository.finishWorkout(workout.id, clock.instant())) {
                restTimerScheduler.cancel()
                _events.send(ActiveWorkoutEvent.Finished(workout.id))
            } else {
                local.update { it.copy(isClosing = false) }
            }
        }
    }

    private fun discard() {
        val workoutId = workout?.id ?: return
        if (local.value.isClosing) return
        local.update { it.copy(isClosing = true) }
        write(onFailure = { local.update { it.copy(isClosing = false) } }) {
            workoutRepository.discardActiveWorkout(workoutId)
            restTimerScheduler.cancel()
            _events.send(ActiveWorkoutEvent.Discarded)
        }
    }

    /**
     * Writes what's been typed and not yet saved: set values and notes, in one transaction.
     * Called with the write lock held.
     */
    private suspend fun saveEdits() {
        val workout = workout?.takeIf { it.isActive } ?: return
        val savedSets = mutableListOf<() -> Unit>()
        val setValues = buildMap {
            workout.exercises.forEach { exercise ->
                exercise.sets.forEach { set ->
                    val fields = setFields[set.id] ?: return@forEach
                    val texts = fields.texts()
                    val values = fields.values()
                    if (values != set.values) {
                        put(set.id, values)
                        savedSets += { fields.markSaved(values, texts) }
                    }
                }
            }
        }
        val notes = workout.exercises.mapNotNull { exercise ->
            exerciseNotes[exercise.id]?.takeIf { it.hasChanged() }?.let { exercise.id to it }
        }
        val exerciseNoteValues = notes.associate { (id, note) -> id to note.current() }
        val workoutNoteValue = workoutNote?.takeIf { it.hasChanged() }?.current()
        val workoutNoteChanged = workoutNote?.hasChanged() == true
        val edits = WorkoutEdits(
            setValues = setValues,
            exerciseNotes = exerciseNoteValues,
            workoutNoteChanged = workoutNoteChanged,
            workoutNote = workoutNoteValue,
        )
        if (edits.isEmpty) return
        workoutRepository.saveEdits(workout.id, edits)
        savedSets.forEach { it() }
        notes.forEach { (id, note) -> note.markSaved(exerciseNoteValues[id]) }
        if (workoutNoteChanged) workoutNote?.markSaved(workoutNoteValue)
    }

    /** Runs [block] after every write before it, and reports a failure instead of crashing. */
    private fun write(onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                // Not cancellable once started: leaving the screen mustn't cut a save in half.
                writes.withLock { withContext(NonCancellable) { block() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure()
                _events.send(ActiveWorkoutEvent.SaveFailed)
            }
        }
    }

    /** Creates text fields for new sets and exercises, and drops those that are gone. */
    private fun syncFields(workout: Workout?, unit: WeightUnit) {
        var changed = false
        if (fieldsUnit != unit) {
            // Values are shown in the unit they're typed in, so a unit change starts afresh.
            setFields.clear()
            fieldsUnit = unit
            changed = true
        }
        val setIds = HashSet<String>()
        val exerciseIds = HashSet<String>()
        workout?.exercises?.forEach { exercise ->
            exerciseIds += exercise.id
            if (exercise.note != null) shownNotes += exercise.id
            if (exercise.id !in exerciseNotes) {
                exerciseNotes[exercise.id] = NoteText(exercise.note)
                changed = true
            }
            exercise.sets.forEach { set ->
                setIds += set.id
                if (set.id !in setFields) {
                    setFields[set.id] = SetFields.from(set.values, unit)
                    changed = true
                }
            }
        }
        if (setFields.keys.retainAll(setIds)) changed = true
        if (exerciseNotes.keys.retainAll(exerciseIds)) changed = true
        if (workout != null && workout.id != workoutNoteFor) {
            workoutNote = NoteText(workout.note)
            workoutNoteFor = workout.id
            changed = true
        }
        if (changed) fieldsVersion.update { it + 1 }
    }

    /** Once reordering is over and the database shows the new order, stop showing it ahead. */
    private fun settleOrder(workout: Workout?, local: LocalState) {
        val order = local.order ?: return
        if (!local.isReordering && (workout == null || workout.exercises.map { it.id } == order)) {
            this.local.update { if (it.order == order && !it.isReordering) it.copy(order = null) else it }
        }
    }

    private fun stateFor(
        workout: Workout?,
        preferences: UserPreferences,
        local: LocalState,
        picker: ExercisePickerUiState?,
    ): ActiveWorkoutUiState {
        if (workout == null) {
            return ActiveWorkoutUiState(
                isLoading = false,
                defaultRestSeconds = preferences.defaultRestSeconds,
                weightUnit = preferences.weightUnit,
                zone = clock.zone,
            )
        }
        val ordered = local.order?.let { order ->
            workout.exercises.sortedBy { exercise -> order.indexOf(exercise.id).let { if (it < 0) Int.MAX_VALUE else it } }
        } ?: workout.exercises
        val exercises = ordered.map { exercise ->
            // Warm-ups aren't numbered (FR-3.10); every other set takes the next number.
            var working = 0
            ActiveExercise(
                item = exercise,
                sets = exercise.sets.map { set ->
                    ActiveSet(
                        set = set,
                        number = if (set.setType == SetType.WARMUP) null else ++working,
                        fields = setFields.getValue(set.id),
                        showMissing = set.id in local.missing,
                    )
                },
                note = exerciseNotes.getValue(exercise.id).state,
                showNote = exercise.id in shownNotes || exercise.id in local.openedNotes,
                restSeconds = RestTimes.secondsFor(exercise, preferences.defaultRestSeconds),
                hasOwnRest = exercise.restSecondsOverride != null || exercise.exercise.defaultRestSeconds != null,
            )
        }
        return ActiveWorkoutUiState(
            isLoading = false,
            workout = workout,
            exercises = exercises,
            workoutNote = workoutNote?.state,
            progress = workout.progress(),
            rest = workout.rest,
            defaultRestSeconds = preferences.defaultRestSeconds,
            weightUnit = preferences.weightUnit,
            zone = clock.zone,
            isReordering = local.isReordering,
            picker = picker,
        )
    }

    private fun editableTexts(): List<String> = buildList {
        setFields.values.forEach { addAll(it.texts()) }
        exerciseNotes.values.forEach { add(it.state.text.toString()) }
        workoutNote?.let { add(it.state.text.toString()) }
    }

    /** What's on screen but not in the database. */
    private data class LocalState(
        /** Discarding or finishing: the screen is on its way out. */
        val isClosing: Boolean = false,
        val isReordering: Boolean = false,
        /** The exercise order being shown ahead of the database while reordering. */
        val order: List<String>? = null,
        /** The picker's selection while it's open, in the order picked; null while closed. */
        val picked: List<String>? = null,
        /** Exercises the user asked to add a note to. */
        val openedNotes: Set<String> = emptySet(),
        /** Sets that were tried with something missing. */
        val missing: Set<String> = emptySet(),
    )

    private companion object {
        const val EDIT_SAVE_DELAY_MILLIS = 400L
    }
}
