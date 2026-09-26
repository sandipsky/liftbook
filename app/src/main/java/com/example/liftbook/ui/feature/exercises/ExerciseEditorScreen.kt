package com.example.liftbook.ui.feature.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.ExerciseNameError
import com.example.liftbook.domain.calculator.ExerciseNames
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.ChoiceChip
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.ExerciseTypeDisplayOrder
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.examplesRes
import com.example.liftbook.ui.components.labelRes
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle

@Composable
fun ExerciseEditorRoute(
    exerciseId: String?,
    initialName: String?,
    onClose: () -> Unit,
    onSaved: (exerciseId: String) -> Unit,
) {
    val viewModel = hiltViewModel<ExerciseEditorViewModel, ExerciseEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(exerciseId, initialName) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExerciseEditorEvent.Saved -> currentOnSaved(event.exerciseId)
                ExerciseEditorEvent.SaveFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.editor_save_failed))
            }
        }
    }

    ExerciseEditorScreen(
        state = state,
        nameState = viewModel.nameState,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            if (action == ExerciseEditorAction.Close) onClose() else viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(
    state: ExerciseEditorUiState,
    nameState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (ExerciseEditorAction) -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val close = {
        if (state.hasUnsavedChanges) confirmDiscard = true else onAction(ExerciseEditorAction.Close)
    }
    BackHandler(enabled = state.hasUnsavedChanges) { confirmDiscard = true }

    Scaffold(
        topBar = {
            LiftBookTopBar(
                title = stringResource(if (state.isEditing) R.string.editor_title_edit else R.string.editor_title_create),
                navigation = TopBarNavigation.Close,
                onNavigationClick = close,
            )
        },
        bottomBar = {
            if (!state.isLoading && !state.isUnavailable) {
                BottomActionBar(
                    text = stringResource(if (state.isEditing) R.string.editor_save_edit else R.string.editor_save_create),
                    onClick = { onAction(ExerciseEditorAction.Save) },
                    // An unchanged edit has nothing to save; a new exercise is validated on tap instead,
                    // so the errors can say what's missing.
                    enabled = !state.isSaving && (!state.isEditing || state.hasUnsavedChanges),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> EditorSkeleton(Modifier.padding(padding))
            state.isUnavailable -> EmptyState(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.editor_unavailable_title),
                body = stringResource(R.string.editor_unavailable_body),
                modifier = Modifier.padding(padding),
                action = {
                    OutlinedButton(onClick = { onAction(ExerciseEditorAction.Close) }) {
                        Text(stringResource(R.string.editor_unavailable_action))
                    }
                },
            )
            else -> EditorForm(
                state = state,
                nameState = nameState,
                onAction = onAction,
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            text = stringResource(
                if (state.isEditing) R.string.editor_discard_body_edit else R.string.editor_discard_body_create,
            ),
            confirmLabel = stringResource(R.string.editor_discard_confirm),
            dismissLabel = stringResource(R.string.editor_discard_dismiss),
            onConfirm = {
                confirmDiscard = false
                onAction(ExerciseEditorAction.Close)
            },
            onDismiss = { confirmDiscard = false },
            destructive = true,
        )
    }
}

@Composable
private fun EditorForm(
    state: ExerciseEditorUiState,
    nameState: TextFieldState,
    onAction: (ExerciseEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        NameField(
            nameState = nameState,
            error = state.nameError,
            // A new, unnamed exercise starts with the keyboard up; edits don't grab focus.
            autoFocus = remember { !state.isEditing && nameState.text.isEmpty() },
        )
        ChoiceSection(
            title = stringResource(R.string.editor_muscle),
            error = if (state.showMuscleError) stringResource(R.string.editor_error_muscle) else null,
        ) {
            MuscleGroup.entries.forEach { muscle ->
                ChoiceChip(
                    selected = state.primaryMuscle == muscle,
                    onClick = { onAction(ExerciseEditorAction.SelectMuscle(muscle)) },
                    label = stringResource(muscle.labelRes()),
                )
            }
        }
        ChoiceSection(
            title = stringResource(R.string.editor_equipment),
            error = if (state.showEquipmentError) stringResource(R.string.editor_error_equipment) else null,
        ) {
            Equipment.entries.forEach { equipment ->
                ChoiceChip(
                    selected = state.equipment == equipment,
                    onClick = { onAction(ExerciseEditorAction.SelectEquipment(equipment)) },
                    label = stringResource(equipment.labelRes()),
                )
            }
        }
        TypeSection(state = state, onAction = onAction)
    }
}

@Composable
private fun NameField(nameState: TextFieldState, error: ExerciseNameError?, autoFocus: Boolean) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val errorText = when (error) {
        ExerciseNameError.BLANK -> stringResource(R.string.editor_error_name_blank)
        ExerciseNameError.TOO_LONG ->
            pluralStringResource(R.plurals.editor_error_name_too_long, ExerciseNames.MAX_LENGTH, ExerciseNames.MAX_LENGTH)
        ExerciseNameError.DUPLICATE ->
            stringResource(R.string.editor_error_name_duplicate, ExerciseNames.normalize(nameState.text.toString()))
        null -> null
    }
    OutlinedTextField(
        state = nameState,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        label = { Text(stringResource(R.string.editor_name_label)) },
        placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
        isError = error != null,
        // The icon pairs with the error colour, so the state never rests on colour alone.
        trailingIcon = if (error != null) {
            { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(IconSize.inline)) }
        } else {
            null
        },
        supportingText = errorText?.let { text -> { Text(text) } },
        inputTransformation = InputTransformation.maxLength(ExerciseNames.MAX_LENGTH),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        onKeyboardAction = { focusManager.clearFocus() },
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.onSurface,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.onSurface,
        ),
    )
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun ChoiceSection(
    title: String,
    error: String?,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column {
        FieldTitle(title)
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = content,
        )
        FieldError(error)
    }
}

/** "How it's logged": a radio list rather than segments, so each option can carry examples. */
@Composable
private fun TypeSection(state: ExerciseEditorUiState, onAction: (ExerciseEditorAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val enabled = !state.isTypeLocked
    Column {
        FieldTitle(stringResource(R.string.editor_type))
        Spacer(Modifier.height(Spacing.xxs))
        Column(Modifier.selectableGroup()) {
            ExerciseTypeDisplayOrder.forEach { type ->
                val selected = state.type == type
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onAction(ExerciseEditorAction.SelectType(type)) },
                        )
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = enabled,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.onSurface,
                            unselectedColor = colors.outline,
                        ),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(type.labelRes()),
                            style = MaterialTheme.typography.rowTitle,
                            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = DISABLED_ALPHA),
                        )
                        Text(
                            text = stringResource(type.examplesRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (state.isTypeLocked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.inline),
                )
                Text(
                    text = stringResource(R.string.editor_type_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FieldTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

/** An inline error, with an icon so it never relies on colour alone. */
@Composable
private fun FieldError(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(ERROR_ANIM_MILLIS)) + expandVertically(tween(ERROR_ANIM_MILLIS)),
        exit = fadeOut(tween(ERROR_ANIM_MILLIS)) + shrinkVertically(tween(ERROR_ANIM_MILLIS)),
    ) {
        Row(
            modifier = Modifier
                .padding(top = Spacing.xs)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(IconSize.inline),
            )
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EditorSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.editor_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl + Spacing.xs), MaterialTheme.shapes.medium)
            repeat(2) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(Spacing.md))
                    SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl + Spacing.xxl), MaterialTheme.shapes.small)
                }
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
private const val ERROR_ANIM_MILLIS = 200

@ThemePreviews
@Composable
private fun ExerciseEditorCreatePreview() {
    LiftBookTheme {
        ExerciseEditorScreen(
            state = ExerciseEditorUiState(primaryMuscle = MuscleGroup.SHOULDERS, equipment = Equipment.CABLE),
            nameState = rememberTextFieldState("Cable Y-Raise"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExerciseEditorErrorsPreview() {
    LiftBookTheme {
        ExerciseEditorScreen(
            state = ExerciseEditorUiState(
                nameError = ExerciseNameError.DUPLICATE,
                showMuscleError = true,
                showEquipmentError = true,
            ),
            nameState = rememberTextFieldState("Bench Press (Barbell)"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExerciseEditorLockedTypePreview() {
    LiftBookTheme {
        ExerciseEditorScreen(
            state = ExerciseEditorUiState(
                isEditing = true,
                primaryMuscle = MuscleGroup.SHOULDERS,
                equipment = Equipment.CABLE,
                isTypeLocked = true,
                hasUnsavedChanges = true,
            ),
            nameState = rememberTextFieldState("Cable Y-Raise"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
