package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.SetPrefill
import com.example.liftbook.domain.calculator.WorkoutNames
import com.example.liftbook.domain.calculator.WorkoutSpan
import com.example.liftbook.domain.calculator.WorkoutTimes
import com.example.liftbook.domain.calculator.searchExercises
import com.example.liftbook.domain.model.ExerciseFilter
import com.example.liftbook.domain.model.ExerciseRevision
import com.example.liftbook.domain.model.SetRevision
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutRevision
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.domain.repository.ExerciseRepository
import com.example.liftbook.domain.repository.SettingsRepository
import com.example.liftbook.domain.repository.WorkoutRepository
import com.example.liftbook.ui.components.ExercisePickerUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Edits a finished workout (FR-4.2): its name and when it was, its notes, and what was done —
 * set values and types, sets and exercises added or removed, their order. Unlike the workout in
 * progress, nothing is written until Save, and then all of it in one transaction, so closing the
 * editor leaves history as it was. Volume and records are derived from the sets, so they follow
 * the save with nothing else to do.
 *
 * Typed values live in [SetFields] and [NoteText], as in the active workout, so a converted
 * weight the user didn't touch is saved exactly as it was loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WorkoutEditorViewModel.Factory::class)
class WorkoutEditorViewModel @AssistedInject constructor(
    @Assisted private val workoutId: String,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val nameState = TextFieldState()

    /** The exercise picker's search field. */
    val pickerQueryState = TextFieldState()

    private val form = MutableStateFlow(Form())

    private val _events = Channel<WorkoutEditorEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEditorEvent> = _events.receiveAsFlow()

    // What's being typed, by set and workout-exercise id.
    private val setFields = HashMap<String, SetFields>()
    private val exerciseNotes = HashMap<String, NoteText>()
    private var workoutNote = NoteText(null)
    private var weightUnit = WeightUnit.KG

    /** The workout as it opened, or as last saved: equal to [snapshot] means nothing to save. */
    private var saved: Snapshot? = null

    /** Ticks whenever anything typed changes. */
    private val typing: Flow<Any> = form
        .map { it.exercises }
        .distinctUntilChanged()
        .flatMapLatest { snapshotFlow { typedTexts() } }

    private val picker: Flow<ExercisePickerUiState?> = form
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

    val uiState: StateFlow<WorkoutEditorUiState> = combine(
        form,
        snapshotFlow { nameState.text.toString() },
        typing,
        picker,
    ) { form, name, _, picker ->
        stateFor(form, name, picker)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutEditorUiState(zone = clock.zone))

    init {
        viewModelScope.launch { load() }
    }

    fun onAction(action: WorkoutEditorAction) {
        when (action) {
            WorkoutEditorAction.Save -> save()
            WorkoutEditorAction.Delete -> delete()

            is WorkoutEditorAction.SetDate -> changeSpan { WorkoutTimes.withDate(it, action.date, clock.zone) }
            is WorkoutEditorAction.SetStartTime -> changeSpan { WorkoutTimes.withStartTime(it, action.time, clock.zone) }
            is WorkoutEditorAction.SetEndTime -> changeSpan { WorkoutTimes.withEndTime(it, action.time, clock.zone) }

            WorkoutEditorAction.OpenPicker -> {
                pickerQueryState.clearText()
                form.update { it.copy(picked = emptyList()) }
            }
            WorkoutEditorAction.ClosePicker -> form.update { it.copy(picked = null) }
            is WorkoutEditorAction.TogglePicked -> form.update { current ->
                val picked = current.picked ?: return@update current
                current.copy(picked = if (action.exerciseId in picked) picked - action.exerciseId else picked + action.exerciseId)
            }
            WorkoutEditorAction.AddPicked -> addPicked()
            is WorkoutEditorAction.RemoveExercise -> removeExercise(action.workoutExerciseId)
            WorkoutEditorAction.StartReordering -> form.update { it.copy(isReordering = true) }
            WorkoutEditorAction.StopReordering -> form.update { it.copy(isReordering = false) }
            is WorkoutEditorAction.MoveExercise -> form.update { current ->
                val exercises = current.exercises
                if (action.from !in exercises.indices || action.to !in exercises.indices || action.from == action.to) {
                    current
                } else {
                    current.copy(exercises = exercises.toMutableList().apply { add(action.to, removeAt(action.from)) })
                }
            }
            is WorkoutEditorAction.ShowExerciseNote ->
                form.update { it.copy(openedNotes = it.openedNotes + action.workoutExerciseId) }

            is WorkoutEditorAction.AddSet -> addSet(action.workoutExerciseId)
            is WorkoutEditorAction.RemoveSet -> {
                setFields.remove(action.setId)
                editSets { sets -> sets.filterNot { it.id == action.setId } }
                form.update { it.copy(missing = it.missing - action.setId) }
            }
            is WorkoutEditorAction.ChangeSetType ->
                editSets { sets -> sets.map { if (it.id == action.setId) it.copy(setType = action.setType) else it } }

            // Navigation; handled by the route.
            WorkoutEditorAction.Close -> Unit
        }
    }

    private suspend fun load() {
        weightUnit = settingsRepository.userPreferences.first().weightUnit
        val workout = workoutRepository.observeWorkout(workoutId).first()
        val finishedAt = workout?.finishedAt
        if (workout == null || finishedAt == null) {
            form.value = Form(isLoading = false, isUnavailable = true)
            return
        }
        nameState.setTextAndPlaceCursorAtEnd(workout.name)
        workoutNote = NoteText(workout.note)
        // History holds only what was done; anything else wouldn't be saved as done.
        val exercises = workout.exercises.map { exercise -> exercise.copy(sets = exercise.sets.filter { it.isCompleted }) }
        exercises.forEach { exercise ->
            exerciseNotes[exercise.id] = NoteText(exercise.note)
            exercise.sets.forEach { set -> setFields[set.id] = SetFields.from(set.values, weightUnit) }
        }
        val loaded = Form(
            isLoading = false,
            span = WorkoutSpan(workout.startedAt, finishedAt),
            exercises = exercises,
            openedNotes = exercises.filter { it.note != null }.mapTo(HashSet()) { it.id },
        )
        saved = snapshot(loaded, workout.name)
        form.value = loaded
    }

    private fun changeSpan(change: (WorkoutSpan) -> WorkoutSpan) = form.update { current ->
        current.span?.let { current.copy(span = change(it)) } ?: current
    }

    /** A new set copies the last working set as it's typed now, as in the workout in progress. */
    private fun addSet(workoutExerciseId: String) {
        val current = form.value
        val exercise = current.exercises.firstOrNull { it.id == workoutExerciseId } ?: return
        val values = SetPrefill.forAddedSet(
            exercise.exercise.type,
            exercise.sets.map { SetPrefill.SetCandidate(it.setType, isCompleted = true, values = setFields[it.id]?.values() ?: it.values) },
        )
        val set = doneSet(SetType.NORMAL, values)
        setFields[set.id] = SetFields.from(values, weightUnit)
        form.value = current.copy(
            exercises = current.exercises.map { if (it.id == workoutExerciseId) it.copy(sets = it.sets + set) else it },
        )
    }

    /** Each picked exercise starts with the sets done last time it was done, as in a workout. */
    private fun addPicked() {
        val picked = form.value.picked ?: return
        form.update { it.copy(picked = null) }
        if (picked.isEmpty()) return
        viewModelScope.launch {
            val library = exerciseRepository.observeLibrary().first().associateBy { it.id }
            val added = picked.mapNotNull(library::get).map { exercise ->
                val lastTime = exerciseRepository.observeLastSession(exercise.id).first()?.sets.orEmpty()
                val sets = SetPrefill.forAddedExercise(exercise.type, lastTime).map { doneSet(it.setType, it.values) }
                WorkoutExercise(id = newId(), exercise = exercise, sets = sets)
            }
            added.forEach { exercise ->
                exerciseNotes[exercise.id] = NoteText(null)
                exercise.sets.forEach { set -> setFields[set.id] = SetFields.from(set.values, weightUnit) }
            }
            form.update { it.copy(exercises = it.exercises + added) }
            added.firstOrNull()?.let { _events.send(WorkoutEditorEvent.ExercisesAdded(it.id)) }
        }
    }

    private fun removeExercise(workoutExerciseId: String) {
        val current = form.value
        val exercise = current.exercises.firstOrNull { it.id == workoutExerciseId } ?: return
        exercise.sets.forEach { setFields.remove(it.id) }
        exerciseNotes.remove(workoutExerciseId)
        form.value = current.copy(
            exercises = current.exercises - exercise,
            openedNotes = current.openedNotes - workoutExerciseId,
            missing = current.missing - exercise.sets.mapTo(HashSet()) { it.id },
        )
    }

    private fun editSets(change: (List<WorkoutSet>) -> List<WorkoutSet>) = form.update { current ->
        current.copy(exercises = current.exercises.map { it.copy(sets = change(it.sets)) })
    }

    /**
     * Checks the edit and saves it whole. A set needs what its type records, as when it was
     * marked done (FR-3.3); a workout with no sets left isn't saved — deleting it is offered.
     */
    private fun save() {
        val current = form.value
        val span = current.span
        if (current.isLoading || current.isUnavailable || current.isSaving || span == null) return
        val name = WorkoutNames.normalize(nameState.text.toString())
        val exercises = current.exercises.map { exercise ->
            val sets = exercise.sets.map { set -> set to setFields[set.id]?.values()?.metricsFor(exercise.exercise.type) }
            exercise to sets
        }
        val missing = exercises.flatMap { (_, sets) -> sets.filter { it.second == null }.map { it.first.id } }.toSet()
        form.update { it.copy(submitted = true, missing = missing) }
        when {
            name.isEmpty() -> send(WorkoutEditorEvent.NameMissing)
            missing.isNotEmpty() -> send(WorkoutEditorEvent.SetsMissing(missing.size))
            exercises.all { (_, sets) -> sets.isEmpty() } -> send(WorkoutEditorEvent.NothingLeft)
            else -> {
                val revision = WorkoutRevision(
                    name = name,
                    startedAt = span.start,
                    finishedAt = span.end,
                    note = workoutNote.current(),
                    exercises = exercises.map { (exercise, sets) ->
                        ExerciseRevision(
                            id = exercise.id,
                            exerciseId = exercise.exercise.id,
                            note = exerciseNotes[exercise.id]?.current(),
                            sets = sets.map { (set, metrics) -> SetRevision(set.id, set.setType, requireNotNull(metrics)) },
                        )
                    },
                )
                write(revision, name)
            }
        }
    }

    private fun write(revision: WorkoutRevision, name: String) {
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val done = try {
                workoutRepository.saveRevision(workoutId, revision)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (done) {
                // Nothing is unsaved any more, so leaving doesn't ask.
                saved = snapshot(form.value, name)
                form.update { it.copy(isSaving = false) }
                _events.send(WorkoutEditorEvent.Saved)
            } else {
                form.update { it.copy(isSaving = false) }
                _events.send(WorkoutEditorEvent.SaveFailed)
            }
        }
    }

    private fun delete() {
        if (form.value.isSaving) return
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                workoutRepository.deleteFinishedWorkout(workoutId)
                _events.send(WorkoutEditorEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(isSaving = false) }
                _events.send(WorkoutEditorEvent.SaveFailed)
            }
        }
    }

    private fun send(event: WorkoutEditorEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun stateFor(form: Form, name: String, picker: ExercisePickerUiState?): WorkoutEditorUiState {
        if (form.isLoading || form.isUnavailable) {
            return WorkoutEditorUiState(isLoading = form.isLoading, isUnavailable = form.isUnavailable, zone = clock.zone)
        }
        val exercises = form.exercises.map { exercise ->
            // Warm-ups aren't numbered (FR-3.10); every other set takes the next number.
            var working = 0
            ActiveExercise(
                item = exercise,
                sets = exercise.sets.map { set ->
                    ActiveSet(
                        set = set,
                        number = if (set.setType == SetType.WARMUP) null else ++working,
                        fields = setFields.getValue(set.id),
                        showMissing = set.id in form.missing,
                    )
                },
                note = exerciseNotes.getValue(exercise.id).state,
                showNote = exercise.id in form.openedNotes,
                // A finished workout has no rest to time.
                restSeconds = 0,
                hasOwnRest = false,
            )
        }
        return WorkoutEditorUiState(
            isLoading = false,
            span = form.span,
            exercises = exercises,
            workoutNote = workoutNote.state,
            weightUnit = weightUnit,
            today = LocalDate.now(clock),
            zone = clock.zone,
            showNameError = form.submitted && WorkoutNames.normalize(name).isEmpty(),
            hasUnsavedChanges = saved?.let { it != snapshot(form, name) } ?: false,
            isSaving = form.isSaving,
            isReordering = form.isReordering,
            picker = picker,
        )
    }

    private fun snapshot(form: Form, name: String) = Snapshot(
        name = WorkoutNames.normalize(name),
        span = form.span,
        note = workoutNote.current(),
        exercises = form.exercises.map { exercise ->
            ExerciseSnapshot(
                id = exercise.id,
                note = exerciseNotes[exercise.id]?.current(),
                sets = exercise.sets.map { set -> SetSnapshot(set.id, set.setType, setFields[set.id]?.values()) },
            )
        },
    )

    private fun typedTexts(): List<String> = buildList {
        setFields.values.forEach { addAll(it.texts()) }
        exerciseNotes.values.forEach { add(it.state.text.toString()) }
        add(workoutNote.state.text.toString())
    }

    /** A set of a finished workout: done, with a new id. */
    private fun doneSet(setType: SetType, values: SetValues) = WorkoutSet(
        id = newId(),
        setType = setType,
        isCompleted = true,
        weightKg = values.weightKg,
        reps = values.reps,
        durationSeconds = values.durationSeconds,
        distanceMeters = values.distanceMeters,
    )

    private fun newId(): String = UUID.randomUUID().toString()

    private data class Form(
        val isLoading: Boolean = true,
        val isUnavailable: Boolean = false,
        val span: WorkoutSpan? = null,
        /** In order, with their sets. Values are in [setFields]; the sets hold what they opened with. */
        val exercises: List<WorkoutExercise> = emptyList(),
        val isReordering: Boolean = false,
        /** The picker's selection while it's open, in the order picked; null while closed. */
        val picked: List<String>? = null,
        /** Exercises whose note field shows: those with a note, and those the user asked to add one to. */
        val openedNotes: Set<String> = emptySet(),
        /** Sets a save found missing values. */
        val missing: Set<String> = emptySet(),
        val submitted: Boolean = false,
        val isSaving: Boolean = false,
    )

    /** What the user can change, as it would be saved: equal snapshots mean nothing to save. */
    private data class Snapshot(
        val name: String,
        val span: WorkoutSpan?,
        val note: String?,
        val exercises: List<ExerciseSnapshot>,
    )

    private data class ExerciseSnapshot(val id: String, val note: String?, val sets: List<SetSnapshot>)

    private data class SetSnapshot(val id: String, val setType: SetType, val values: SetValues?)

    @AssistedFactory
    interface Factory {
        fun create(workoutId: String): WorkoutEditorViewModel
    }
}
