package com.example.liftbook.ui.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.WorkoutNames
import com.example.liftbook.domain.calculator.WorkoutSpan
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.ExercisePickerSheet
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.NoteField
import com.example.liftbook.ui.components.PastDatePickerDialog
import com.example.liftbook.ui.components.ReorderableItem
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TimeOfDayPickerDialog
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.rememberReorderableListState
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.components.workoutDurationText
import com.example.liftbook.ui.feature.workout.components.ExerciseBlock
import com.example.liftbook.ui.feature.workout.components.ReorderRow
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun WorkoutEditorRoute(
    workoutId: String,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel = hiltViewModel<WorkoutEditorViewModel, WorkoutEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(workoutId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val resources = LocalResources.current
    var nothingLeft by rememberSaveable { mutableStateOf(false) }
    val currentState by rememberUpdatedState(state)
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnDeleted by rememberUpdatedState(onDeleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutEditorEvent.Saved -> currentOnSaved()
                WorkoutEditorEvent.Deleted -> currentOnDeleted()
                WorkoutEditorEvent.SaveFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.workout_editor_save_failed))
                WorkoutEditorEvent.NameMissing -> listState.animateScrollToItem(0)
                is WorkoutEditorEvent.SetsMissing ->
                    snackbarHostState.showSnackbar(resources.getQuantityString(R.plurals.workout_editor_sets_missing, event.count, event.count))
                WorkoutEditorEvent.NothingLeft -> nothingLeft = true
                is WorkoutEditorEvent.ExercisesAdded -> {
                    // Wait for the new exercise to show, then bring it into view.
                    val index = withTimeoutOrNull(SCROLL_WAIT_MILLIS) {
                        snapshotFlow { currentState.exercises.indexOfFirst { it.id == event.firstWorkoutExerciseId } }.first { it >= 0 }
                    }
                    if (index != null) listState.animateScrollToItem(FIRST_EXERCISE_INDEX + index)
                }
            }
        }
    }

    WorkoutEditorScreen(
        state = state,
        nameState = viewModel.nameState,
        pickerQueryState = viewModel.pickerQueryState,
        snackbarHostState = snackbarHostState,
        listState = listState,
        showNothingLeft = nothingLeft,
        onDismissNothingLeft = { nothingLeft = false },
        onAction = { action -> if (action == WorkoutEditorAction.Close) onClose() else viewModel.onAction(action) },
    )
}

/**
 * A finished workout, opened to edit (FR-4.2): its name and when it was at the top, then what was
 * done, in the same set table the workout was logged in — without done toggles, since every set
 * in history is done. Save sits at the bottom where the thumb is; leaving with changes asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    state: WorkoutEditorUiState,
    nameState: TextFieldState,
    pickerQueryState: TextFieldState,
    onAction: (WorkoutEditorAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    showNothingLeft: Boolean = false,
    onDismissNothingLeft: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val focusManager = LocalFocusManager.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var removingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf<TimeField?>(null) }
    val loaded = !state.isLoading && !state.isUnavailable
    val close = {
        if (state.hasUnsavedChanges) confirmDiscard = true else onAction(WorkoutEditorAction.Close)
    }

    BackHandler(enabled = state.isReordering) { onAction(WorkoutEditorAction.StopReordering) }
    BackHandler(enabled = !state.isReordering && state.hasUnsavedChanges) { confirmDiscard = true }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(if (state.isReordering) R.string.workout_reorder_title else R.string.workout_editor_title),
                navigation = if (state.isReordering) TopBarNavigation.Back else TopBarNavigation.Close,
                onNavigationClick = { if (state.isReordering) onAction(WorkoutEditorAction.StopReordering) else close() },
                actions = {
                    when {
                        !loaded -> Unit
                        state.isReordering -> TextButton(onClick = { onAction(WorkoutEditorAction.StopReordering) }) {
                            Text(stringResource(R.string.workout_reorder_done))
                        }
                        else -> EditorMenu(
                            canReorder = state.exercises.size > 1,
                            onReorder = {
                                focusManager.clearFocus()
                                onAction(WorkoutEditorAction.StartReordering)
                            },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (loaded && !state.isReordering) {
                BottomActionBar(
                    text = stringResource(R.string.workout_editor_save),
                    onClick = {
                        focusManager.clearFocus()
                        onAction(WorkoutEditorAction.Save)
                    },
                    enabled = !state.isSaving && state.hasUnsavedChanges,
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
                title = stringResource(R.string.workout_detail_not_found_title),
                body = stringResource(R.string.workout_detail_not_found_body),
                modifier = Modifier.padding(padding),
                action = {
                    OutlinedButton(onClick = { onAction(WorkoutEditorAction.Close) }) {
                        Text(stringResource(R.string.workout_detail_not_found_action))
                    }
                },
            )
            // Padding rather than content padding, so the list's viewport ends above the save bar
            // and the keyboard, and a focused field scrolls into view above them.
            else -> EditorList(
                state = state,
                nameState = nameState,
                listState = listState,
                onAction = onAction,
                onPickTime = { picking = it },
                onRemoveExercise = { exercise ->
                    if (exercise.sets.isNotEmpty()) removingExerciseId = exercise.id else onAction(WorkoutEditorAction.RemoveExercise(exercise.id))
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    state.picker?.let { picker ->
        ExercisePickerSheet(
            results = picker.results,
            selectedIds = picker.selectedIds,
            query = picker.query,
            queryState = pickerQueryState,
            libraryCount = picker.libraryCount,
            onToggle = { onAction(WorkoutEditorAction.TogglePicked(it)) },
            onConfirm = { onAction(WorkoutEditorAction.AddPicked) },
            onDismiss = { onAction(WorkoutEditorAction.ClosePicker) },
        )
    }

    val span = state.span
    when (val field = picking) {
        null -> Unit
        TimeField.Date -> if (span != null) {
            PastDatePickerDialog(
                selected = span.start.atZone(state.zone).toLocalDate(),
                today = state.today,
                onPick = { date ->
                    picking = null
                    onAction(WorkoutEditorAction.SetDate(date))
                },
                onDismiss = { picking = null },
            )
        }
        TimeField.Start, TimeField.End -> if (span != null) {
            val shown = if (field == TimeField.Start) span.start else span.end
            TimeOfDayPickerDialog(
                title = stringResource(if (field == TimeField.Start) R.string.workout_editor_pick_start else R.string.workout_editor_pick_end),
                selected = shown.atZone(state.zone).toLocalTime(),
                onPick = { time ->
                    picking = null
                    onAction(if (field == TimeField.Start) WorkoutEditorAction.SetStartTime(time) else WorkoutEditorAction.SetEndTime(time))
                },
                onDismiss = { picking = null },
            )
        }
    }

    removingExerciseId?.let { id ->
        val exercise = state.exercises.firstOrNull { it.id == id }
        if (exercise == null) {
            removingExerciseId = null
        } else {
            ConfirmDialog(
                title = stringResource(R.string.workout_remove_exercise_title, exercise.item.exercise.name),
                text = stringResource(R.string.workout_editor_remove_exercise_body),
                confirmLabel = stringResource(R.string.workout_remove_exercise_confirm),
                dismissLabel = stringResource(R.string.workout_remove_exercise_dismiss),
                onConfirm = {
                    removingExerciseId = null
                    onAction(WorkoutEditorAction.RemoveExercise(id))
                },
                onDismiss = { removingExerciseId = null },
                destructive = true,
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            text = stringResource(R.string.workout_editor_discard_body),
            confirmLabel = stringResource(R.string.editor_discard_confirm),
            dismissLabel = stringResource(R.string.editor_discard_dismiss),
            onConfirm = {
                confirmDiscard = false
                onAction(WorkoutEditorAction.Close)
            },
            onDismiss = { confirmDiscard = false },
            destructive = true,
        )
    }

    if (showNothingLeft) {
        ConfirmDialog(
            title = stringResource(R.string.workout_editor_nothing_title),
            text = stringResource(R.string.workout_editor_nothing_body),
            confirmLabel = stringResource(R.string.workout_editor_nothing_delete),
            dismissLabel = stringResource(R.string.workout_editor_nothing_dismiss),
            onConfirm = {
                onDismissNothingLeft()
                onAction(WorkoutEditorAction.Delete)
            },
            onDismiss = onDismissNothingLeft,
            destructive = true,
        )
    }
}

/** Which of the workout's times a picker is open for. */
private enum class TimeField { Date, Start, End }

@Composable
private fun EditorList(
    state: WorkoutEditorUiState,
    nameState: TextFieldState,
    listState: LazyListState,
    onAction: (WorkoutEditorAction) -> Unit,
    onPickTime: (TimeField) -> Unit,
    onRemoveExercise: (ActiveExercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val exerciseCount = state.exercises.size
    val reorder = rememberReorderableListState(
        listState = listState,
        canMoveTo = { index -> index - REORDER_FIRST_INDEX in 0 until exerciseCount },
        onMove = { from, to -> onAction(WorkoutEditorAction.MoveExercise(from - REORDER_FIRST_INDEX, to - REORDER_FIRST_INDEX)) },
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.lg),
    ) {
        if (state.isReordering) {
            item(key = "reorderHint", contentType = "hint") {
                Text(
                    text = stringResource(R.string.workout_reorder_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs, bottom = Spacing.md),
                )
            }
            itemsIndexed(state.exercises, key = { _, item -> item.id }, contentType = { _, _ -> "reorder" }) { index, item ->
                ReorderableItem(state = reorder, index = index + REORDER_FIRST_INDEX) { isDragging ->
                    ReorderRow(
                        item = item,
                        reorder = reorder,
                        isDragging = isDragging,
                        canMoveUp = index > 0,
                        canMoveDown = index < exerciseCount - 1,
                        onMove = { offset -> onAction(WorkoutEditorAction.MoveExercise(index, index + offset)) },
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "name", contentType = "name") {
            NameField(
                nameState = nameState,
                showError = state.showNameError,
                modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            )
        }
        item(key = "when", contentType = "when") {
            state.span?.let { span ->
                WhenFields(
                    span = span,
                    zone = state.zone,
                    today = state.today,
                    onPick = onPickTime,
                    modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md),
                )
            }
        }
        item(key = "note", contentType = "note") {
            state.workoutNote?.let { note ->
                NoteField(
                    state = note,
                    placeholder = stringResource(R.string.workout_note_placeholder),
                    label = stringResource(R.string.workout_note_label),
                    modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.lg),
                )
            }
        }
        item(key = "exercisesHeader", contentType = "header") {
            SectionHeader(
                title = stringResource(R.string.workout_editor_exercises),
                trailing = exerciseCount.takeIf { it > 0 }?.toString(),
                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
            )
        }
        itemsIndexed(state.exercises, key = { _, item -> item.id }, contentType = { _, _ -> "exercise" }) { _, item ->
            ExerciseBlock(
                item = item,
                weightUnit = state.weightUnit,
                onToggleDone = null,
                onChangeSetType = { id, type -> onAction(WorkoutEditorAction.ChangeSetType(id, type)) },
                onRemoveSet = { onAction(WorkoutEditorAction.RemoveSet(it)) },
                onAddSet = { onAction(WorkoutEditorAction.AddSet(item.id)) },
                onEditRest = null,
                onAddNote = { onAction(WorkoutEditorAction.ShowExerciseNote(item.id)) },
                onReorder = {
                    focusManager.clearFocus()
                    onAction(WorkoutEditorAction.StartReordering)
                },
                onRemove = { onRemoveExercise(item) },
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
            )
        }
        item(key = "add", contentType = "add") {
            AddExercises(
                isEmpty = state.exercises.isEmpty(),
                onAdd = {
                    // Otherwise focus returns to the last field when the sheet closes, and the keyboard with it.
                    focusManager.clearFocus()
                    onAction(WorkoutEditorAction.OpenPicker)
                },
            )
        }
    }
}

@Composable
private fun NameField(nameState: TextFieldState, showError: Boolean, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        state = nameState,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.workout_editor_name_label)) },
        isError = showError,
        // The icon pairs with the error colour, so the state never rests on colour alone.
        trailingIcon = if (showError) {
            { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(IconSize.inline)) }
        } else {
            null
        },
        supportingText = if (showError) {
            { Text(stringResource(R.string.workout_editor_error_name_blank)) }
        } else {
            null
        },
        inputTransformation = InputTransformation.maxLength(WorkoutNames.MAX_LENGTH),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        onKeyboardAction = { focusManager.clearFocus() },
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.onSurface,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.onSurface,
        ),
    )
}

/**
 * When the workout was (FR-4.2): the date across the top, start and end beneath, and how long
 * that makes it. Each opens the system picker. An end before the start means past midnight.
 */
@Composable
private fun WhenFields(
    span: WorkoutSpan,
    zone: ZoneId,
    today: LocalDate,
    onPick: (TimeField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = span.start.atZone(zone)
    val end = span.end.atZone(zone)
    val duration = workoutDurationText(span.duration.seconds)
    Column(modifier.fillMaxWidth()) {
        PickerField(
            label = stringResource(R.string.workout_editor_date),
            value = workoutDateText(start.toLocalDate(), today),
            onClick = { onPick(TimeField.Date) },
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PickerField(
                label = stringResource(R.string.workout_editor_start),
                value = timeOfDayText(start.toLocalTime()),
                onClick = { onPick(TimeField.Start) },
                modifier = Modifier.weight(1f),
            )
            PickerField(
                label = stringResource(R.string.workout_editor_end),
                value = timeOfDayText(end.toLocalTime()),
                onClick = { onPick(TimeField.End) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = if (end.toLocalDate() != start.toLocalDate()) {
                stringResource(R.string.workout_editor_duration_next_day, duration)
            } else {
                stringResource(R.string.workout_editor_duration, duration)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs),
        )
    }
}

/** A value that opens a picker: drawn like the name field above it, label inside, so the form reads as one. */
@Composable
private fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val spoken = stringResource(R.string.workout_editor_field_spoken, label, value)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = PickerFieldHeight)
            .border(FieldOutline, colors.outline, shape)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.workout_editor_field_click_label), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.tabularNumbers(), color = colors.onSurface, maxLines = 1)
    }
}

@Composable
private fun EditorMenu(canReorder: Boolean, onReorder: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_menu_reorder)) },
                leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                enabled = canReorder,
                onClick = {
                    expanded = false
                    onReorder()
                },
            )
        }
    }
}

@Composable
private fun AddExercises(isEmpty: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
        if (isEmpty) {
            Text(
                text = stringResource(R.string.workout_editor_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }
        FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().heightIn(min = AddButtonHeight),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
            Spacer(Modifier.size(Spacing.xs))
            Text(stringResource(R.string.workout_add_exercises))
        }
    }
}

@Composable
private fun EditorSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.workout_editor_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(PickerFieldHeight), MaterialTheme.shapes.medium)
            Spacer(Modifier.height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(PickerFieldHeight), MaterialTheme.shapes.medium)
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                repeat(2) { SkeletonBlock(Modifier.weight(1f).height(PickerFieldHeight), MaterialTheme.shapes.medium) }
            }
            Spacer(Modifier.height(Spacing.xl))
            repeat(2) {
                SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl * 4), MaterialTheme.shapes.large)
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

/** The LazyColumn index of the first exercise: after the name, times, note and heading. */
private const val FIRST_EXERCISE_INDEX = 4

/** The LazyColumn index of the first exercise while reordering: after the hint. */
private const val REORDER_FIRST_INDEX = 1
private const val SCROLL_WAIT_MILLIS = 2_000L
private val PickerFieldHeight = 56.dp
private val FieldOutline = 1.dp
private val AddButtonHeight = 48.dp

private fun previewState(unit: WeightUnit = WeightUnit.KG): WorkoutEditorUiState {
    val active = WorkoutPreviewData.state(unit = unit)
    val start = WorkoutPreviewData.startedAt.minusSeconds(2 * 24 * 3_600)
    return WorkoutEditorUiState(
        isLoading = false,
        span = WorkoutSpan(start, start.plusSeconds(64 * 60)),
        exercises = active.exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { it.copy(set = it.set.copy(isCompleted = true)) })
        },
        workoutNote = TextFieldState("Paused the first rep of every bench set."),
        weightUnit = unit,
        today = LocalDate.of(2026, 9, 25),
        zone = WorkoutPreviewData.zone,
        hasUnsavedChanges = true,
    )
}

@ThemePreviews
@Composable
private fun WorkoutEditorPreview() {
    LiftBookTheme {
        WorkoutEditorScreen(
            state = previewState(),
            nameState = rememberTextFieldState("Push"),
            pickerQueryState = rememberTextFieldState(),
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun WorkoutEditorLoadingPreview() {
    LiftBookTheme {
        WorkoutEditorScreen(
            state = WorkoutEditorUiState(),
            nameState = rememberTextFieldState(),
            pickerQueryState = rememberTextFieldState(),
            onAction = {},
        )
    }
}
