package com.example.liftbook.ui.feature.exercises

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.calculator.ExerciseNameError
import com.example.liftbook.domain.calculator.ExerciseNames
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseDraft
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.repository.ExerciseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

/** Creates a custom exercise (FR-1.2) or edits one (FR-1.3). */
@HiltViewModel(assistedFactory = ExerciseEditorViewModel.Factory::class)
class ExerciseEditorViewModel @AssistedInject constructor(
    /** Null to create a new exercise. */
    @Assisted("exerciseId") private val exerciseId: String?,
    @Assisted("initialName") initialName: String?,
    private val repository: ExerciseRepository,
) : ViewModel() {

    /** The name field. Editable state lives here so typing never round-trips through [uiState]. */
    val nameState = TextFieldState(initialName.orEmpty().take(ExerciseNames.MAX_LENGTH))

    private val form = MutableStateFlow(
        if (exerciseId == null) {
            Form(saved = Fields(ExerciseNames.normalize(nameState.text.toString()), null, null, ExerciseType.STRENGTH))
        } else {
            Form(isLoading = true)
        },
    )

    private val _events = Channel<ExerciseEditorEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseEditorEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ExerciseEditorUiState> = combine(
        form,
        snapshotFlow { nameState.text.toString() },
        repository.observeLibrary(),
    ) { form, name, library ->
        val nameError = ExerciseNames.validate(name, library, editingId = exerciseId)
        ExerciseEditorUiState(
            isEditing = exerciseId != null,
            isLoading = form.isLoading,
            isUnavailable = form.isUnavailable,
            primaryMuscle = form.muscle,
            equipment = form.equipment,
            type = form.type,
            isTypeLocked = form.isTypeLocked,
            // Point out a clash as soon as it's typed, but don't scold an empty field before a save attempt.
            nameError = nameError.takeIf { it != ExerciseNameError.BLANK || form.submitted },
            showMuscleError = form.submitted && form.muscle == null,
            showEquipmentError = form.submitted && form.equipment == null,
            hasUnsavedChanges = form.saved != null && form.fields(name) != form.saved,
            isSaving = form.isSaving,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExerciseEditorUiState(isEditing = exerciseId != null, isLoading = exerciseId != null),
    )

    init {
        if (exerciseId != null) viewModelScope.launch { load(exerciseId) }
    }

    fun onAction(action: ExerciseEditorAction) {
        when (action) {
            is ExerciseEditorAction.SelectMuscle -> form.update { it.copy(muscle = action.muscleGroup).withSuggestedType() }
            is ExerciseEditorAction.SelectEquipment -> form.update { it.copy(equipment = action.equipment).withSuggestedType() }
            is ExerciseEditorAction.SelectType -> form.update {
                if (it.isTypeLocked) it else it.copy(type = action.type, typeChosen = true)
            }
            ExerciseEditorAction.Save -> save()
            // Navigation; handled by the route.
            ExerciseEditorAction.Close -> Unit
        }
    }

    private suspend fun load(id: String) {
        val exercise = repository.getExercise(id)
        if (exercise == null || !exercise.isEditable) {
            form.value = Form(isUnavailable = true)
            return
        }
        nameState.setTextAndPlaceCursorAtEnd(exercise.name)
        form.value = Form(
            muscle = exercise.primaryMuscle,
            equipment = exercise.equipment,
            type = exercise.type,
            typeChosen = true,
            isTypeLocked = repository.hasLoggedSets(id),
            saved = Fields(exercise.name, exercise.primaryMuscle, exercise.equipment, exercise.type),
        )
    }

    private fun save() {
        val current = form.value
        if (current.isLoading || current.isUnavailable || current.isSaving) return
        val name = nameState.text.toString()
        // Mark saving before anything suspends, so a double tap can't create the exercise twice.
        form.update { it.copy(submitted = true, isSaving = true) }
        viewModelScope.launch {
            val library = repository.observeLibrary().first()
            val muscle = current.muscle
            val equipment = current.equipment
            if (ExerciseNames.validate(name, library, editingId = exerciseId) != null || muscle == null || equipment == null) {
                form.update { it.copy(isSaving = false) }
                return@launch
            }
            val draft = ExerciseDraft(name = name, primaryMuscle = muscle, equipment = equipment, type = current.type)
            try {
                val id = exerciseId?.also { repository.updateCustomExercise(it, draft) }
                    ?: repository.createCustomExercise(draft)
                form.update { it.copy(isSaving = false, saved = it.fields(name)) }
                _events.send(ExerciseEditorEvent.Saved(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(isSaving = false) }
                _events.send(ExerciseEditorEvent.SaveFailed)
            }
        }
    }

    /** Until the user picks a type, it follows what the muscle and equipment suggest. */
    private fun Form.withSuggestedType(): Form =
        if (typeChosen || isTypeLocked) this else copy(type = ExerciseType.suggestedFor(muscle, equipment))

    private data class Form(
        val isLoading: Boolean = false,
        val isUnavailable: Boolean = false,
        val muscle: MuscleGroup? = null,
        val equipment: Equipment? = null,
        val type: ExerciseType = ExerciseType.STRENGTH,
        val typeChosen: Boolean = false,
        val isTypeLocked: Boolean = false,
        val submitted: Boolean = false,
        val isSaving: Boolean = false,
        /** The values as last saved (or as the form opened), for detecting unsaved changes. */
        val saved: Fields? = null,
    ) {
        fun fields(name: String) = Fields(ExerciseNames.normalize(name), muscle, equipment, type)
    }

    private data class Fields(
        val name: String,
        val muscle: MuscleGroup?,
        val equipment: Equipment?,
        val type: ExerciseType,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("exerciseId") exerciseId: String?,
            @Assisted("initialName") initialName: String?,
        ): ExerciseEditorViewModel
    }
}
