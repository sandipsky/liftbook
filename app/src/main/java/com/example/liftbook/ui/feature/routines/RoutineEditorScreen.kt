package com.example.liftbook.ui.feature.routines

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.RoutineNameError
import com.example.liftbook.domain.calculator.RoutineNames
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.ExercisePickerSheet
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.NumberField
import com.example.liftbook.ui.components.NumberFieldKind
import com.example.liftbook.ui.components.ReorderableItem
import com.example.liftbook.ui.components.ReorderableListState
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.distanceLabelRes
import com.example.liftbook.ui.components.dragHandle
import com.example.liftbook.ui.components.exerciseMetaSpoken
import com.example.liftbook.ui.components.exerciseMetaText
import com.example.liftbook.ui.components.rememberReorderableListState
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle

@Composable
fun RoutineEditorRoute(
    routineId: String?,
    onClose: () -> Unit,
    onSaved: (routineId: String) -> Unit,
) {
    val viewModel = hiltViewModel<RoutineEditorViewModel, RoutineEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(routineId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RoutineEditorEvent.Saved -> currentOnSaved(event.routineId)
                RoutineEditorEvent.SaveFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.routine_editor_save_failed))
            }
        }
    }

    RoutineEditorScreen(
        state = state,
        nameState = viewModel.nameState,
        pickerQueryState = viewModel.pickerQueryState,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            if (action == RoutineEditorAction.Close) onClose() else viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    state: RoutineEditorUiState,
    nameState: TextFieldState,
    pickerQueryState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (RoutineEditorAction) -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val close = {
        if (state.hasUnsavedChanges) confirmDiscard = true else onAction(RoutineEditorAction.Close)
    }
    BackHandler(enabled = state.hasUnsavedChanges) { confirmDiscard = true }

    Scaffold(
        topBar = {
            LiftBookTopBar(
                title = stringResource(if (state.isEditing) R.string.routine_editor_title_edit else R.string.routine_editor_title_create),
                navigation = TopBarNavigation.Close,
                onNavigationClick = close,
            )
        },
        bottomBar = {
            if (!state.isLoading && !state.isUnavailable) {
                BottomActionBar(
                    text = stringResource(if (state.isEditing) R.string.routine_editor_save_edit else R.string.routine_editor_save_create),
                    onClick = { onAction(RoutineEditorAction.Save) },
                    // An unchanged edit has nothing to save; a new routine is validated on tap instead,
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
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.routine_detail_not_found_title),
                body = stringResource(R.string.routine_detail_not_found_body),
                modifier = Modifier.padding(padding),
                action = {
                    OutlinedButton(onClick = { onAction(RoutineEditorAction.Close) }) {
                        Text(stringResource(R.string.routine_detail_not_found_action))
                    }
                },
            )
            // Padding rather than content padding, so the list's viewport ends above the save bar and
            // the keyboard, and a focused field scrolls into view above them.
            else -> EditorList(state = state, nameState = nameState, onAction = onAction, modifier = Modifier.padding(padding))
        }
    }

    state.picker?.let { picker ->
        ExercisePickerSheet(
            results = picker.results,
            selectedIds = picker.selectedIds,
            query = picker.query,
            queryState = pickerQueryState,
            libraryCount = picker.libraryCount,
            onToggle = { onAction(RoutineEditorAction.TogglePicked(it)) },
            onConfirm = { onAction(RoutineEditorAction.AddPicked) },
            onDismiss = { onAction(RoutineEditorAction.ClosePicker) },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            text = stringResource(
                if (state.isEditing) R.string.routine_editor_discard_body_edit else R.string.routine_editor_discard_body_create,
            ),
            confirmLabel = stringResource(R.string.editor_discard_confirm),
            dismissLabel = stringResource(R.string.editor_discard_dismiss),
            onConfirm = {
                confirmDiscard = false
                onAction(RoutineEditorAction.Close)
            },
            onDismiss = { confirmDiscard = false },
            destructive = true,
        )
    }
}

@Composable
private fun EditorList(
    state: RoutineEditorUiState,
    nameState: TextFieldState,
    onAction: (RoutineEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val exerciseCount = state.exercises.size
    val reorder = rememberReorderableListState(
        listState = listState,
        canMoveTo = { index -> index - FIRST_EXERCISE_INDEX in 0 until exerciseCount },
        onMove = { from, to -> onAction(RoutineEditorAction.MoveExercise(from - FIRST_EXERCISE_INDEX, to - FIRST_EXERCISE_INDEX)) },
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.lg),
    ) {
        item(key = "name", contentType = "name") {
            NameField(
                nameState = nameState,
                error = state.nameError,
                // A new, unnamed routine starts with the keyboard up; edits don't grab focus.
                autoFocus = remember { !state.isEditing && nameState.text.isEmpty() },
                modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            )
        }
        item(key = "exercisesHeader", contentType = "header") {
            SectionHeader(
                title = stringResource(R.string.routine_editor_exercises),
                trailing = exerciseCount.takeIf { it > 0 }?.toString(),
                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
            )
        }
        itemsIndexed(state.exercises, key = { _, item -> item.key }, contentType = { _, _ -> "exercise" }) { index, item ->
            ReorderableItem(state = reorder, index = index + FIRST_EXERCISE_INDEX) { isDragging ->
                ExerciseBlock(
                    item = item,
                    weightUnit = state.weightUnit,
                    reorder = reorder,
                    isDragging = isDragging,
                    canMoveUp = index > 0,
                    canMoveDown = index < exerciseCount - 1,
                    onMove = { offset -> onAction(RoutineEditorAction.MoveExercise(index, index + offset)) },
                    onRemove = { onAction(RoutineEditorAction.RemoveExercise(item.key)) },
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                )
            }
        }
        item(key = "add", contentType = "add") {
            AddExercises(
                isEmpty = state.exercises.isEmpty(),
                showError = state.showNoExercisesError,
                onAdd = {
                    // Otherwise focus returns to the last field when the sheet closes, and the keyboard with it.
                    focusManager.clearFocus()
                    onAction(RoutineEditorAction.OpenPicker)
                },
            )
        }
    }
}

@Composable
private fun NameField(
    nameState: TextFieldState,
    error: RoutineNameError?,
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val errorText = when (error) {
        RoutineNameError.BLANK -> stringResource(R.string.routine_editor_error_name_blank)
        RoutineNameError.TOO_LONG -> pluralStringResource(
            R.plurals.routine_editor_error_name_too_long, RoutineNames.MAX_LENGTH, RoutineNames.MAX_LENGTH,
        )
        RoutineNameError.DUPLICATE ->
            stringResource(R.string.routine_editor_error_name_duplicate, RoutineNames.normalize(nameState.text.toString()))
        null -> null
    }
    OutlinedTextField(
        state = nameState,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        label = { Text(stringResource(R.string.routine_editor_name_label)) },
        placeholder = { Text(stringResource(R.string.routine_editor_name_placeholder)) },
        isError = error != null,
        // The icon pairs with the error colour, so the state never rests on colour alone.
        trailingIcon = if (error != null) {
            { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(IconSize.inline)) }
        } else {
            null
        },
        supportingText = errorText?.let { text -> { Text(text) } },
        inputTransformation = InputTransformation.maxLength(RoutineNames.MAX_LENGTH),
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

/**
 * One exercise and its target. The handle drags it to a new place; the menu offers the same
 * move one step at a time, which also works with TalkBack. Lifted while dragged: a tone up and
 * a shadow, the one place a shadow means something.
 */
@Composable
private fun ExerciseBlock(
    item: RoutineEditorExercise,
    weightUnit: WeightUnit,
    reorder: ReorderableListState,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (offset: Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val container by animateColorAsState(
        targetValue = if (isDragging) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        animationSpec = tween(LIFT_MILLIS),
        label = "blockColor",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) LiftedElevation else 0.dp,
        animationSpec = tween(LIFT_MILLIS),
        label = "blockElevation",
    )
    val metaSpoken = exerciseMetaSpoken(item.exercise)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(elevation, shape)
            .clip(shape)
            .background(container)
            .padding(bottom = Spacing.md),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Spacing.xxl)
                    .dragHandle(reorder, item.key)
                    // Not usable from TalkBack; the menu's Move up / Move down is the accessible path.
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.inline),
                )
            }
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(
                    text = item.exercise.name,
                    style = MaterialTheme.typography.rowTitle,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = exerciseMetaText(item.exercise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = metaSpoken },
                )
            }
            ExerciseMenu(
                exerciseName = item.exercise.name,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMove = onMove,
                onRemove = onRemove,
            )
        }
        TargetRow(
            fields = item.fields,
            type = item.exercise.type,
            weightUnit = weightUnit,
            showSetsError = item.showSetsError,
            modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.md, top = Spacing.xs),
        )
        InlineError(
            message = if (item.showSetsError) {
                pluralStringResource(R.plurals.routine_editor_error_sets, SetTarget.MAX_SETS, SetTarget.MIN_SETS, SetTarget.MAX_SETS)
            } else {
                null
            },
            modifier = Modifier.padding(start = Spacing.xxl, end = Spacing.md),
        )
    }
}

/** The values each set starts from, laid out for what the exercise records (FR-3.3). */
@Composable
private fun TargetRow(
    fields: TargetFields,
    type: ExerciseType,
    weightUnit: WeightUnit,
    showSetsError: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        NumberField(
            state = fields.sets,
            label = stringResource(R.string.target_sets),
            kind = NumberFieldKind.Whole,
            maxLength = SETS_MAX_DIGITS,
            isError = showSetsError,
            modifier = Modifier.weight(1f),
        )
        when (type) {
            ExerciseType.STRENGTH -> {
                NumberField(
                    state = fields.reps,
                    label = stringResource(R.string.target_reps),
                    kind = NumberFieldKind.Whole,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    state = fields.weight,
                    label = stringResource(R.string.target_weight),
                    kind = NumberFieldKind.Decimal,
                    unit = stringResource(weightUnit.weightLabelRes()),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(WIDE_FIELD_WEIGHT),
                )
            }
            ExerciseType.BODYWEIGHT -> {
                NumberField(
                    state = fields.reps,
                    label = stringResource(R.string.target_reps),
                    kind = NumberFieldKind.Whole,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
                // Keeps the columns lined up with the exercises above and below.
                Spacer(Modifier.weight(WIDE_FIELD_WEIGHT))
            }
            ExerciseType.CARDIO -> {
                NumberField(
                    state = fields.duration,
                    label = stringResource(R.string.target_time),
                    kind = NumberFieldKind.Duration,
                    modifier = Modifier.weight(CARDIO_FIELD_WEIGHT),
                )
                NumberField(
                    state = fields.distance,
                    label = stringResource(R.string.target_distance),
                    kind = NumberFieldKind.Decimal,
                    unit = stringResource(weightUnit.distanceLabelRes()),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(CARDIO_FIELD_WEIGHT),
                )
            }
        }
    }
}

@Composable
private fun ExerciseMenu(
    exerciseName: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (offset: Int) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.routine_editor_options, exerciseName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routine_editor_move_up)) },
                leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                enabled = canMoveUp,
                onClick = {
                    expanded = false
                    onMove(-1)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routine_editor_move_down)) },
                leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                enabled = canMoveDown,
                onClick = {
                    expanded = false
                    onMove(1)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routine_editor_remove)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun AddExercises(isEmpty: Boolean, showError: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
        if (isEmpty) {
            Text(
                text = stringResource(R.string.routine_editor_exercises_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
        FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = AddButtonHeight),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
            Spacer(Modifier.size(Spacing.xs))
            Text(stringResource(R.string.routine_editor_add_exercises))
        }
        InlineError(message = if (showError) stringResource(R.string.routine_editor_error_no_exercises) else null)
    }
}

/** An inline error, with an icon so it never relies on colour alone. */
@Composable
private fun InlineError(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
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
        contentDescription = stringResource(R.string.routine_editor_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl + Spacing.xs), MaterialTheme.shapes.medium)
            Spacer(Modifier.height(Spacing.xl))
            SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(Spacing.md))
            Spacer(Modifier.height(Spacing.md))
            repeat(3) {
                SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonBlockHeight), MaterialTheme.shapes.large)
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

/** The LazyColumn index of the first exercise, after the name field and the section header. */
private const val FIRST_EXERCISE_INDEX = 2
private const val SETS_MAX_DIGITS = 2
private const val WIDE_FIELD_WEIGHT = 1.6f
private const val CARDIO_FIELD_WEIGHT = 1.3f
private const val LIFT_MILLIS = 150
private const val ERROR_ANIM_MILLIS = 200
private val LiftedElevation = 8.dp
private val AddButtonHeight = 48.dp
private val SkeletonBlockHeight = Spacing.xxl + Spacing.xxl + Spacing.lg

@ThemePreviews
@Composable
private fun RoutineEditorPreview() {
    LiftBookTheme {
        RoutineEditorScreen(
            state = RoutinePreviewData.editorState(),
            nameState = rememberTextFieldState("Push"),
            pickerQueryState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun RoutineEditorEmptyPreview() {
    LiftBookTheme {
        RoutineEditorScreen(
            state = RoutineEditorUiState(nameError = RoutineNameError.BLANK, showNoExercisesError = true),
            nameState = rememberTextFieldState(),
            pickerQueryState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun RoutineEditorLoadingPreview() {
    LiftBookTheme {
        RoutineEditorScreen(
            state = RoutineEditorUiState(isEditing = true, isLoading = true),
            nameState = rememberTextFieldState(),
            pickerQueryState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
