package com.example.liftbook.ui.feature.routines

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.RoutineNameError
import com.example.liftbook.domain.calculator.RoutineNames
import com.example.liftbook.domain.calculator.searchExercises
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseFilter
import com.example.liftbook.domain.model.RoutineDraft
import com.example.liftbook.domain.model.RoutineExerciseDraft
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.repository.ExerciseRepository
import com.example.liftbook.domain.repository.RoutineRepository
import com.example.liftbook.domain.repository.SettingsRepository
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
import java.util.UUID

/**
 * Creates a routine (FR-2.1) or edits one (FR-2.2): a name, and an ordered list of exercises,
 * each with a target of sets, reps and weight — or time and distance for cardio. Nothing is
 * written until Save, so leaving discards the edit as a whole.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = RoutineEditorViewModel.Factory::class)
class RoutineEditorViewModel @AssistedInject constructor(
    /** Null to create a new routine. */
    @Assisted private val routineId: String?,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** The name field. Editable state lives here so typing never round-trips through [uiState]. */
    val nameState = TextFieldState()

    /** The exercise picker's search field. */
    val pickerQueryState = TextFieldState()

    private val form = MutableStateFlow(
        if (routineId == null) Form(saved = Snapshot(name = "", exercises = emptyList())) else Form(isLoading = true),
    )

    private val _events = Channel<RoutineEditorEvent>(Channel.BUFFERED)
    val events: Flow<RoutineEditorEvent> = _events.receiveAsFlow()

    /** Ticks whenever any target field of any exercise is edited. */
    private val fieldEdits: Flow<Any> = form
        .map { it.items }
        .distinctUntilChanged()
        .flatMapLatest { items -> snapshotFlow { items.map { it.fields.texts() } } }

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

    val uiState: StateFlow<RoutineEditorUiState> = combine(
        form,
        snapshotFlow { nameState.text.toString() },
        fieldEdits,
        routineRepository.observeRoutines(),
        picker,
    ) { form, name, _, routines, picker ->
        val nameError = RoutineNames.validate(name, routines, editingId = routineId)
        RoutineEditorUiState(
            isEditing = routineId != null,
            isLoading = form.isLoading,
            isUnavailable = form.isUnavailable,
            exercises = form.items.map { item ->
                RoutineEditorExercise(
                    key = item.key,
                    exercise = item.exercise,
                    fields = item.fields,
                    showSetsError = form.submitted && !item.fields.hasValidSets(),
                )
            },
            weightUnit = form.weightUnit,
            // Point out a clash as soon as it's typed, but don't scold an empty field before a save attempt.
            nameError = nameError.takeIf { it != RoutineNameError.BLANK || form.submitted },
            showNoExercisesError = form.submitted && form.items.isEmpty(),
            hasUnsavedChanges = form.saved != null && form.snapshot(name) != form.saved,
            isSaving = form.isSaving,
            picker = picker,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RoutineEditorUiState(isEditing = routineId != null, isLoading = routineId != null),
    )

    init {
        viewModelScope.launch {
            val unit = settingsRepository.userPreferences.first().weightUnit
            if (routineId == null) {
                form.update { it.copy(weightUnit = unit) }
            } else {
                load(routineId, unit)
            }
        }
    }

    fun onAction(action: RoutineEditorAction) {
        when (action) {
            RoutineEditorAction.OpenPicker -> {
                pickerQueryState.clearText()
                form.update { it.copy(picked = emptyList()) }
            }
            RoutineEditorAction.ClosePicker -> form.update { it.copy(picked = null) }
            is RoutineEditorAction.TogglePicked -> form.update { current ->
                val picked = current.picked ?: return@update current
                current.copy(picked = if (action.exerciseId in picked) picked - action.exerciseId else picked + action.exerciseId)
            }
            RoutineEditorAction.AddPicked -> addPicked()
            is RoutineEditorAction.MoveExercise -> form.update { current ->
                val items = current.items
                if (action.from !in items.indices || action.to !in items.indices || action.from == action.to) {
                    current
                } else {
                    current.copy(items = items.toMutableList().apply { add(action.to, removeAt(action.from)) })
                }
            }
            is RoutineEditorAction.RemoveExercise -> form.update { current ->
                current.copy(items = current.items.filterNot { it.key == action.key })
            }
            RoutineEditorAction.Save -> save()
            // Navigation; handled by the route.
            RoutineEditorAction.Close -> Unit
        }
    }

    private suspend fun load(id: String, unit: WeightUnit) {
        val routine = routineRepository.getRoutine(id)
        if (routine == null) {
            form.value = Form(isUnavailable = true, weightUnit = unit)
            return
        }
        nameState.setTextAndPlaceCursorAtEnd(routine.name)
        val items = routine.exercises.map { exercise ->
            Item(key = exercise.id, id = exercise.id, exercise = exercise.exercise, fields = TargetFields.from(exercise.target, unit))
        }
        val loaded = Form(items = items, weightUnit = unit)
        form.value = loaded.copy(saved = loaded.snapshot(routine.name))
    }

    private fun addPicked() {
        val picked = form.value.picked ?: return
        viewModelScope.launch {
            val library = exerciseRepository.observeLibrary().first().associateBy(Exercise::id)
            form.update { current ->
                val added = picked.mapNotNull(library::get).map { exercise ->
                    Item(
                        key = UUID.randomUUID().toString(),
                        id = null,
                        exercise = exercise,
                        fields = TargetFields.from(SetTarget.defaultFor(exercise.type), current.weightUnit),
                    )
                }
                current.copy(items = current.items + added, picked = null)
            }
        }
    }

    private fun save() {
        val current = form.value
        if (current.isLoading || current.isUnavailable || current.isSaving) return
        val name = nameState.text.toString()
        // Mark saving before anything suspends, so a double tap can't create the routine twice.
        form.update { it.copy(submitted = true, isSaving = true) }
        viewModelScope.launch {
            val routines = routineRepository.observeRoutines().first()
            val targets = current.items.map { it.fields.toTarget(current.weightUnit) }
            if (RoutineNames.validate(name, routines, editingId = routineId) != null ||
                current.items.isEmpty() ||
                targets.any { it == null }
            ) {
                form.update { it.copy(isSaving = false) }
                return@launch
            }
            val draft = RoutineDraft(
                name = name,
                exercises = current.items.zip(targets.filterNotNull()) { item, target ->
                    RoutineExerciseDraft(exerciseId = item.exercise.id, target = target, id = item.id)
                },
            )
            try {
                val id = routineId?.also { routineRepository.updateRoutine(it, draft) }
                    ?: routineRepository.createRoutine(draft)
                form.update { it.copy(isSaving = false, saved = it.snapshot(name)) }
                _events.send(RoutineEditorEvent.Saved(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(isSaving = false) }
                _events.send(RoutineEditorEvent.SaveFailed)
            }
        }
    }

    private data class Form(
        val isLoading: Boolean = false,
        val isUnavailable: Boolean = false,
        val items: List<Item> = emptyList(),
        val weightUnit: WeightUnit = WeightUnit.KG,
        val submitted: Boolean = false,
        val isSaving: Boolean = false,
        /** The picker's selection while it's open, in the order picked; null while closed. */
        val picked: List<String>? = null,
        /** The form as last saved (or as it opened), for detecting unsaved changes. */
        val saved: Snapshot? = null,
    ) {
        fun snapshot(name: String) = Snapshot(
            name = RoutineNames.normalize(name),
            exercises = items.map { it.key to it.fields.texts() },
        )
    }

    /** An exercise in the form. Compared by identity: its fields are live text states. */
    private class Item(
        val key: String,
        /** The routine exercise it was loaded from; null when added in this edit. */
        val id: String?,
        val exercise: Exercise,
        val fields: TargetFields,
    )

    /** What the user can change, as text: equal snapshots mean nothing to save. */
    private data class Snapshot(
        val name: String,
        val exercises: List<Pair<String, TargetTexts>>,
    )

    @AssistedFactory
    interface Factory {
        fun create(routineId: String?): RoutineEditorViewModel
    }
}
