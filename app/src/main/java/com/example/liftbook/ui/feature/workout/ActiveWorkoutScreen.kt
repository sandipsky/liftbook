package com.example.liftbook.ui.feature.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.WorkoutProgress
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.ExercisePickerSheet
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.NoteField
import com.example.liftbook.ui.components.ReorderableItem
import com.example.liftbook.ui.components.RestDurationDialog
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.StatTile
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.displayWeight
import com.example.liftbook.ui.components.elapsedText
import com.example.liftbook.ui.components.rememberReorderableListState
import com.example.liftbook.ui.components.spokenDuration
import com.example.liftbook.ui.components.elapsedSeconds
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.feature.workout.components.ExerciseBlock
import com.example.liftbook.ui.feature.workout.components.ReorderRow
import com.example.liftbook.ui.feature.workout.components.RestTimerBar
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

@Composable
fun ActiveWorkoutRoute(
    onNavigateUp: () -> Unit,
    onDiscarded: () -> Unit,
    onFinished: (workoutId: String) -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val alerts = rememberRestAlertIssue()
    val currentState by rememberUpdatedState(state)
    val currentOnDiscarded by rememberUpdatedState(onDiscarded)
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ActiveWorkoutEvent.Discarded -> currentOnDiscarded()
                is ActiveWorkoutEvent.Finished -> currentOnFinished(event.workoutId)
                is ActiveWorkoutEvent.ExercisesAdded -> {
                    // Wait for the new exercise to arrive from the database, then bring it into view.
                    val index = withTimeoutOrNull(SCROLL_WAIT_MILLIS) {
                        snapshotFlow { currentState.exercises.indexOfFirst { it.id == event.firstWorkoutExerciseId } }.first { it >= 0 }
                    }
                    if (index != null) listState.animateScrollToItem(FIRST_EXERCISE_INDEX + index)
                }
                ActiveWorkoutEvent.SaveFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.workout_action_failed))
            }
        }
    }

    // Whatever was typed a moment ago is saved before the screen goes out of view (FR-3.7).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(ActiveWorkoutAction.SaveNow) }

    // Notifications are asked for in context: when the first rest starts, the moment they're for.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { alerts.recheck() }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val resting = state.rest != null
    LaunchedEffect(resting) {
        if (resting && !askedForNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ActiveWorkoutScreen(
        state = state,
        pickerQueryState = viewModel.pickerQueryState,
        now = { now },
        alertIssue = alerts.issue,
        snackbarHostState = snackbarHostState,
        listState = listState,
        onFixAlerts = { issue -> openRestAlertSettings(context, issue) },
        onAction = { action ->
            if (action == ActiveWorkoutAction.NavigateUp) onNavigateUp() else viewModel.onAction(action)
        },
    )
}

/**
 * The workout in progress: the core screen. Everything the user does mid-set — logging a set,
 * marking it done, watching the rest — sits in the lower part of the screen, big enough to hit
 * one-handed. [now] is read only by the clocks, so the once-a-second tick redraws only them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    pickerQueryState: TextFieldState,
    now: () -> Instant,
    onAction: (ActiveWorkoutAction) -> Unit,
    alertIssue: RestAlertIssue? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    onFixAlerts: (RestAlertIssue) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headerScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val focusManager = LocalFocusManager.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var removingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var restDialogExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultRestDialog by rememberSaveable { mutableStateOf(false) }
    val workout = state.workout

    BackHandler(enabled = state.isReordering) { onAction(ActiveWorkoutAction.StopReordering) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = if (state.isReordering) stringResource(R.string.workout_reorder_title) else workout?.name.orEmpty(),
                navigation = TopBarNavigation.Back,
                onNavigationClick = {
                    if (state.isReordering) onAction(ActiveWorkoutAction.StopReordering) else onAction(ActiveWorkoutAction.NavigateUp)
                },
                showTitle = state.isReordering || headerScrolledAway,
                subtitle = if (workout != null && !state.isReordering) {
                    { Text(elapsedText(workout.startedAt, now()), style = MaterialTheme.typography.labelMedium.tabularNumbers()) }
                } else {
                    null
                },
                actions = {
                    when {
                        workout == null -> Unit
                        state.isReordering -> TextButton(onClick = { onAction(ActiveWorkoutAction.StopReordering) }) {
                            Text(stringResource(R.string.workout_reorder_done))
                        }
                        else -> {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    confirmFinish = true
                                },
                                contentPadding = PaddingValues(horizontal = Spacing.md),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(stringResource(R.string.workout_finish))
                            }
                            WorkoutMenu(
                                canReorder = state.exercises.size > 1,
                                onDefaultRest = { defaultRestDialog = true },
                                onReorder = {
                                    focusManager.clearFocus()
                                    onAction(ActiveWorkoutAction.StartReordering)
                                },
                                onDiscard = { confirmDiscard = true },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            // Always present, so the content keeps clear of the navigation bar and keyboard even
            // with no rest running; the rest bar rides above the keyboard while a set is typed.
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                RestSlot(
                    rest = state.rest?.takeUnless { state.isReordering },
                    now = now,
                    alertIssue = alertIssue,
                    onAction = onAction,
                    onFixAlerts = onFixAlerts,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = when {
            state.isLoading -> WorkoutContent.Loading
            workout == null -> WorkoutContent.None
            else -> WorkoutContent.Workout
        }
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "workoutContent",
        ) { target ->
            when (target) {
                WorkoutContent.Loading -> WorkoutSkeleton(Modifier.padding(padding))
                WorkoutContent.None -> EmptyState(
                    icon = Icons.Outlined.FitnessCenter,
                    title = stringResource(R.string.workout_none_title),
                    body = stringResource(R.string.workout_none_body),
                    modifier = Modifier.padding(padding),
                    action = {
                        OutlinedButton(onClick = { onAction(ActiveWorkoutAction.NavigateUp) }) {
                            Text(stringResource(R.string.workout_none_action))
                        }
                    },
                )
                WorkoutContent.Workout -> if (workout != null) {
                    WorkoutList(
                        workout = workout,
                        state = state,
                        now = now,
                        listState = listState,
                        // Padding, not content padding, at the bottom: the viewport ends above the
                        // rest bar and keyboard, so a focused field scrolls into view above them.
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                        topPadding = padding.calculateTopPadding(),
                        onAction = onAction,
                        onEditRest = { restDialogExerciseId = it },
                        onRemoveExercise = { exercise ->
                            if (exercise.hasCompletedSets) {
                                removingExerciseId = exercise.id
                            } else {
                                onAction(ActiveWorkoutAction.RemoveExercise(exercise.id))
                            }
                        },
                    )
                }
            }
        }
    }

    state.picker?.let { picker ->
        ExercisePickerSheet(
            results = picker.results,
            selectedIds = picker.selectedIds,
            query = picker.query,
            queryState = pickerQueryState,
            libraryCount = picker.libraryCount,
            onToggle = { onAction(ActiveWorkoutAction.TogglePicked(it)) },
            onConfirm = { onAction(ActiveWorkoutAction.AddPicked) },
            onDismiss = { onAction(ActiveWorkoutAction.ClosePicker) },
        )
    }

    if (confirmFinish) {
        FinishDialog(
            progress = state.progress,
            onFinish = {
                confirmFinish = false
                onAction(ActiveWorkoutAction.Finish)
            },
            onDiscard = {
                confirmFinish = false
                onAction(ActiveWorkoutAction.Discard)
            },
            onDismiss = { confirmFinish = false },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.workout_discard_title),
            text = stringResource(R.string.workout_discard_body),
            confirmLabel = stringResource(R.string.workout_discard_confirm),
            dismissLabel = stringResource(R.string.workout_discard_dismiss),
            onConfirm = {
                confirmDiscard = false
                onAction(ActiveWorkoutAction.Discard)
            },
            onDismiss = { confirmDiscard = false },
            destructive = true,
        )
    }

    removingExerciseId?.let { id ->
        val exercise = state.exercises.firstOrNull { it.id == id }
        if (exercise == null) {
            removingExerciseId = null
        } else {
            ConfirmDialog(
                title = stringResource(R.string.workout_remove_exercise_title, exercise.item.exercise.name),
                text = stringResource(R.string.workout_remove_exercise_body),
                confirmLabel = stringResource(R.string.workout_remove_exercise_confirm),
                dismissLabel = stringResource(R.string.workout_remove_exercise_dismiss),
                onConfirm = {
                    removingExerciseId = null
                    onAction(ActiveWorkoutAction.RemoveExercise(id))
                },
                onDismiss = { removingExerciseId = null },
                destructive = true,
            )
        }
    }

    restDialogExerciseId?.let { id ->
        val exercise = state.exercises.firstOrNull { it.id == id }
        if (exercise == null) {
            restDialogExerciseId = null
        } else {
            RestDurationDialog(
                title = stringResource(R.string.rest_dialog_title),
                body = stringResource(R.string.rest_dialog_body_exercise, exercise.item.exercise.name),
                selected = if (exercise.hasOwnRest) exercise.restSeconds else null,
                defaultSeconds = state.defaultRestSeconds,
                onSelect = { seconds ->
                    restDialogExerciseId = null
                    onAction(ActiveWorkoutAction.SetExerciseRest(id, seconds))
                },
                onDismiss = { restDialogExerciseId = null },
            )
        }
    }

    if (defaultRestDialog) {
        RestDurationDialog(
            title = stringResource(R.string.rest_dialog_title_default),
            body = stringResource(R.string.rest_dialog_body_default),
            selected = state.defaultRestSeconds,
            onSelect = { seconds ->
                defaultRestDialog = false
                if (seconds != null) onAction(ActiveWorkoutAction.SetDefaultRest(seconds))
            },
            onDismiss = { defaultRestDialog = false },
        )
    }
}

private enum class WorkoutContent { Loading, None, Workout }

@Composable
private fun WorkoutList(
    workout: Workout,
    state: ActiveWorkoutUiState,
    now: () -> Instant,
    listState: LazyListState,
    topPadding: Dp,
    onAction: (ActiveWorkoutAction) -> Unit,
    onEditRest: (workoutExerciseId: String) -> Unit,
    onRemoveExercise: (ActiveExercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val exerciseCount = state.exercises.size
    val reorder = rememberReorderableListState(
        listState = listState,
        canMoveTo = { index -> index - FIRST_EXERCISE_INDEX in 0 until exerciseCount },
        onMove = { from, to -> onAction(ActiveWorkoutAction.MoveExercise(from - FIRST_EXERCISE_INDEX, to - FIRST_EXERCISE_INDEX)) },
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = Spacing.lg),
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
                ReorderableItem(state = reorder, index = index + FIRST_EXERCISE_INDEX) { isDragging ->
                    ReorderRow(
                        item = item,
                        reorder = reorder,
                        isDragging = isDragging,
                        canMoveUp = index > 0,
                        canMoveDown = index < exerciseCount - 1,
                        onMove = { offset -> onAction(ActiveWorkoutAction.MoveExercise(index, index + offset)) },
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                    )
                }
            }
        } else {
            item(key = "header", contentType = "header") {
                WorkoutHeader(workout = workout, state = state, now = now)
            }
            itemsIndexed(state.exercises, key = { _, item -> item.id }, contentType = { _, _ -> "exercise" }) { _, item ->
                ExerciseBlock(
                    item = item,
                    weightUnit = state.weightUnit,
                    onToggleDone = { onAction(ActiveWorkoutAction.ToggleSetDone(it)) },
                    onChangeSetType = { id, type -> onAction(ActiveWorkoutAction.ChangeSetType(id, type)) },
                    onRemoveSet = { onAction(ActiveWorkoutAction.RemoveSet(it)) },
                    onAddSet = { onAction(ActiveWorkoutAction.AddSet(item.id)) },
                    onEditRest = { onEditRest(item.id) },
                    onAddNote = { onAction(ActiveWorkoutAction.ShowExerciseNote(item.id)) },
                    onReorder = {
                        focusManager.clearFocus()
                        onAction(ActiveWorkoutAction.StartReordering)
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
                        onAction(ActiveWorkoutAction.OpenPicker)
                    },
                )
            }
        }
    }
}

/**
 * The top of the workout: what it is, and the numbers that say how it's going — time (FR-3.6),
 * volume and sets done — with the workout's note beneath (FR-3.9).
 */
@Composable
private fun WorkoutHeader(workout: Workout, state: ActiveWorkoutUiState, now: () -> Instant) {
    val startedAt = timeOfDayText(workout.startedAt.atZone(state.zone).toLocalTime())
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs, bottom = Spacing.md),
    ) {
        // The accent marks a live workout; the label says so, so it isn't carried by colour.
        Text(
            text = stringResource(R.string.workout_in_progress),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = workout.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.workout_started_at, startedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ElapsedStat(startedAt = workout.startedAt, now = now, modifier = Modifier.weight(1f))
            VolumeStat(volumeKg = state.progress.volumeKg, weightUnit = state.weightUnit, modifier = Modifier.weight(1f))
            SetsStat(progress = state.progress, modifier = Modifier.weight(1f))
        }
        state.workoutNote?.let { note ->
            Spacer(Modifier.height(Spacing.lg))
            NoteField(
                state = note,
                placeholder = stringResource(R.string.workout_note_placeholder),
                label = stringResource(R.string.workout_note_label),
            )
        }
    }
}

/** Elapsed time, redrawn each second on its own (FR-3.6). */
@Composable
private fun ElapsedStat(startedAt: Instant, now: () -> Instant, modifier: Modifier = Modifier) {
    val at = now()
    StatTile(
        label = stringResource(R.string.workout_stat_time),
        value = elapsedText(startedAt, at),
        spokenValue = spokenDuration(elapsedSeconds(startedAt, at)),
        modifier = modifier,
    )
}

@Composable
private fun VolumeStat(volumeKg: Double, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    StatTile(
        label = stringResource(R.string.workout_stat_volume),
        value = displayWeight(volumeKg, weightUnit, maxFractionDigits = 0),
        unit = stringResource(weightUnit.weightLabelRes()),
        modifier = modifier,
    )
}

@Composable
private fun SetsStat(progress: WorkoutProgress, modifier: Modifier = Modifier) {
    StatTile(
        label = stringResource(R.string.workout_stat_sets),
        value = stringResource(R.string.workout_sets_progress, progress.completedSets, progress.totalSets),
        spokenValue = pluralStringResource(
            R.plurals.workout_sets_progress_spoken,
            progress.totalSets,
            progress.completedSets,
            progress.totalSets,
        ),
        modifier = modifier,
    )
}

@Composable
private fun AddExercises(isEmpty: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
        if (isEmpty) {
            Text(
                text = stringResource(R.string.workout_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.workout_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
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

/**
 * The rest bar while a rest runs, and after it ends until it's dismissed or the next set starts
 * another. It never steps aside on its own mid-workout: a bar vanishing under a thumb reaching
 * for its Done would land the tap on the set behind it. Only a long-stale one clears itself.
 */
@Composable
private fun RestSlot(
    rest: RestTimer?,
    now: () -> Instant,
    alertIssue: RestAlertIssue?,
    onAction: (ActiveWorkoutAction) -> Unit,
    onFixAlerts: (RestAlertIssue) -> Unit,
) {
    val visible = rest != null && now().isBefore(rest.endsAt.plusSeconds(REST_LINGER_SECONDS))
    val shown = remember { LastRest() }
    if (rest != null) shown.value = rest
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(REST_BAR_MILLIS)) + expandVertically(tween(REST_BAR_MILLIS)),
        exit = fadeOut(tween(REST_BAR_MILLIS)) + shrinkVertically(tween(REST_BAR_MILLIS)),
    ) {
        shown.value?.let { showing ->
            RestTimerBar(
                rest = showing,
                now = now,
                alertIssue = alertIssue,
                onAdjust = { onAction(ActiveWorkoutAction.AdjustRest(it)) },
                onSkip = { onAction(ActiveWorkoutAction.SkipRest) },
                onFixAlerts = { alertIssue?.let(onFixAlerts) },
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
        }
    }
}

/** The last rest shown, so the bar keeps showing it while it animates away. */
private class LastRest {
    var value: RestTimer? = null
}

@Composable
private fun WorkoutMenu(
    canReorder: Boolean,
    onDefaultRest: () -> Unit,
    onReorder: () -> Unit,
    onDiscard: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_menu_default_rest)) },
                leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDefaultRest()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_menu_reorder)) },
                leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                enabled = canReorder,
                onClick = {
                    expanded = false
                    onReorder()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_discard)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDiscard()
                },
            )
        }
    }
}

/**
 * Asks before finishing only when finishing loses something (FR-3.8): with nothing done there's
 * nothing to save, so the choice is to keep going or discard; with sets left undone, it says
 * they won't be saved. With every set done it finishes straight away.
 */
@Composable
private fun FinishDialog(
    progress: WorkoutProgress,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    when {
        progress.completedSets == 0 -> ConfirmDialog(
            title = stringResource(R.string.finish_nothing_title),
            text = stringResource(R.string.finish_nothing_body),
            confirmLabel = stringResource(R.string.finish_nothing_discard),
            dismissLabel = stringResource(R.string.finish_dismiss),
            onConfirm = onDiscard,
            onDismiss = onDismiss,
            destructive = true,
        )
        progress.incompleteSets > 0 -> ConfirmDialog(
            title = stringResource(R.string.finish_incomplete_title),
            text = pluralStringResource(R.plurals.finish_incomplete_body, progress.incompleteSets, progress.incompleteSets),
            confirmLabel = stringResource(R.string.finish_confirm),
            dismissLabel = stringResource(R.string.finish_dismiss),
            onConfirm = onFinish,
            onDismiss = onDismiss,
        )
        else -> LaunchedEffect(Unit) { onFinish() }
    }
}

@Composable
private fun WorkoutSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.workout_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.25f).height(Spacing.sm))
            SkeletonBlock(Modifier.padding(vertical = Spacing.xs).fillMaxWidth(0.5f).height(Spacing.lg))
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.sm))
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) {
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(Spacing.sm))
                        Spacer(Modifier.height(Spacing.xs))
                        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(Spacing.lg))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            repeat(2) {
                SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonBlockHeight), MaterialTheme.shapes.large)
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

/** The LazyColumn index of the first exercise, after the header (or the reorder hint). */
private const val FIRST_EXERCISE_INDEX = 1
private const val CONTENT_FADE_MILLIS = 200
private const val REST_BAR_MILLIS = 200
private const val SCROLL_WAIT_MILLIS = 2_000L

/** How long an undismissed "Rest over" stays up: as long as its notification does. */
private const val REST_LINGER_SECONDS = 10 * 60L
private val AddButtonHeight = 48.dp
private val SkeletonBlockHeight = Spacing.xxl * 4

@ThemePreviews
@Composable
private fun ActiveWorkoutPreview() {
    LiftBookTheme {
        ActiveWorkoutScreen(
            state = WorkoutPreviewData.state(),
            pickerQueryState = rememberTextFieldState(),
            now = { WorkoutPreviewData.now },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ActiveWorkoutAlertsOffPreview() {
    LiftBookTheme {
        ActiveWorkoutScreen(
            state = WorkoutPreviewData.state(unit = WeightUnit.LB),
            pickerQueryState = rememberTextFieldState(),
            now = { WorkoutPreviewData.now },
            alertIssue = RestAlertIssue.NotificationsOff,
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ActiveWorkoutEmptyPreview() {
    val empty = WorkoutPreviewData.workout.copy(name = "Evening workout", routineId = null, exercises = emptyList(), rest = null)
    LiftBookTheme {
        ActiveWorkoutScreen(
            state = WorkoutPreviewData.state(workout = empty),
            pickerQueryState = rememberTextFieldState(),
            now = { WorkoutPreviewData.now },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ActiveWorkoutReorderPreview() {
    LiftBookTheme {
        ActiveWorkoutScreen(
            state = WorkoutPreviewData.state(isReordering = true),
            pickerQueryState = rememberTextFieldState(),
            now = { WorkoutPreviewData.now },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ActiveWorkoutNonePreview() {
    LiftBookTheme {
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState(isLoading = false),
            pickerQueryState = rememberTextFieldState(),
            now = { WorkoutPreviewData.now },
            onAction = {},
        )
    }
}
