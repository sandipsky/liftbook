package com.example.liftbook.ui.feature.routines

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.RoutineExercise
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.Fact
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.SetTargetText
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.WorkoutInProgressDialog
import com.example.liftbook.ui.components.relativeDayText
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle
import com.example.liftbook.ui.theme.tabularNumbers

@Composable
fun RoutineDetailRoute(
    routineId: String,
    onNavigateUp: () -> Unit,
    onEdit: (routineId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenRoutine: (routineId: String) -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel = hiltViewModel<RoutineDetailViewModel, RoutineDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(routineId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var otherWorkoutName by rememberSaveable { mutableStateOf<String?>(null) }
    val currentOnOpenWorkout by rememberUpdatedState(onOpenWorkout)
    val currentOnOpenRoutine by rememberUpdatedState(onOpenRoutine)
    val currentOnDeleted by rememberUpdatedState(onDeleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RoutineDetailEvent.OpenWorkout -> currentOnOpenWorkout()
                is RoutineDetailEvent.OtherWorkoutActive -> otherWorkoutName = event.workoutName
                RoutineDetailEvent.StartFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.start_failed))
                is RoutineDetailEvent.Duplicated -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.routine_duplicated_message, event.name),
                        actionLabel = resources.getString(R.string.routine_duplicated_open),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) currentOnOpenRoutine(event.routineId)
                }
                RoutineDetailEvent.DuplicateFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.routine_duplicate_failed))
                RoutineDetailEvent.Deleted -> currentOnDeleted()
            }
        }
    }

    RoutineDetailScreen(
        state = state,
        otherWorkoutName = otherWorkoutName,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                RoutineDetailAction.NavigateUp -> onNavigateUp()
                RoutineDetailAction.Edit -> onEdit(routineId)
                is RoutineDetailAction.OpenExercise -> onOpenExercise(action.exerciseId)
                is RoutineDetailAction.OpenRoutine -> onOpenRoutine(action.routineId)
                RoutineDetailAction.ResumeWorkout -> {
                    otherWorkoutName = null
                    onOpenWorkout()
                }
                RoutineDetailAction.DismissOtherWorkout -> otherWorkoutName = null
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    state: RoutineDetailUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (RoutineDetailAction) -> Unit,
    /** Set while the "another workout is in progress" dialog is up. */
    otherWorkoutName: String? = null,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The name moves into the top bar only once the headline has scrolled out of view.
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val routine = state.routine

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = routine?.name.orEmpty(),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(RoutineDetailAction.NavigateUp) },
                showTitle = headlineScrolledAway,
                actions = {
                    if (routine != null) DetailActions(onAction = onAction, onDelete = { confirmDelete = true })
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (routine != null && routine.exercises.isNotEmpty()) {
                BottomActionBar(
                    text = stringResource(R.string.routine_detail_start),
                    onClick = { onAction(RoutineDetailAction.Start) },
                    enabled = !state.isStarting,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = when {
            state.isLoading -> DetailContent.Loading
            routine == null -> DetailContent.NotFound
            else -> DetailContent.Loaded
        }
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "routineContent",
        ) { target ->
            when (target) {
                DetailContent.Loading -> DetailSkeleton(Modifier.padding(padding))
                DetailContent.NotFound -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.routine_detail_not_found_title),
                    body = stringResource(R.string.routine_detail_not_found_body),
                    modifier = Modifier.padding(padding),
                    action = {
                        OutlinedButton(onClick = { onAction(RoutineDetailAction.NavigateUp) }) {
                            Text(stringResource(R.string.routine_detail_not_found_action))
                        }
                    },
                )
                DetailContent.Loaded -> if (routine != null) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + Spacing.lg,
                        ),
                    ) {
                        item(key = "header", contentType = "header") { DetailHeader(routine, state) }
                        if (routine.exercises.isEmpty()) {
                            item(key = "empty", contentType = "empty") {
                                EmptyState(
                                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                                    title = stringResource(R.string.routine_detail_empty_title),
                                    body = stringResource(R.string.routine_detail_empty_body),
                                    modifier = Modifier.padding(top = Spacing.xs),
                                    action = {
                                        Button(onClick = { onAction(RoutineDetailAction.Edit) }) {
                                            Text(stringResource(R.string.routine_detail_empty_action))
                                        }
                                    },
                                )
                            }
                        }
                        itemsIndexed(routine.exercises, key = { _, item -> item.id }, contentType = { _, _ -> "exercise" }) { index, item ->
                            RoutineExerciseRow(
                                number = index + 1,
                                item = item,
                                weightUnit = state.weightUnit,
                                onClick = { onAction(RoutineDetailAction.OpenExercise(item.exercise.id)) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && routine != null) {
        ConfirmDialog(
            title = stringResource(R.string.routine_delete_title, routine.name),
            text = stringResource(R.string.routine_delete_body),
            confirmLabel = stringResource(R.string.routine_delete_confirm),
            dismissLabel = stringResource(R.string.routine_delete_dismiss),
            onConfirm = {
                confirmDelete = false
                onAction(RoutineDetailAction.Delete)
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
    otherWorkoutName?.let { name ->
        WorkoutInProgressDialog(
            workoutName = name,
            onResume = { onAction(RoutineDetailAction.ResumeWorkout) },
            onDismiss = { onAction(RoutineDetailAction.DismissOtherWorkout) },
        )
    }
}

private enum class DetailContent { Loading, NotFound, Loaded }

@Composable
private fun DetailActions(onAction: (RoutineDetailAction) -> Unit, onDelete: () -> Unit) {
    IconButton(onClick = { onAction(RoutineDetailAction.Edit) }) {
        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.routine_detail_edit))
    }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routine_detail_duplicate)) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onAction(RoutineDetailAction.Duplicate)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routine_detail_delete)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DetailHeader(routine: Routine, state: RoutineDetailUiState) {
    val lastDone = routine.lastPerformedAt?.atZone(state.zone)?.toLocalDate()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs, bottom = Spacing.lg),
    ) {
        Text(
            text = routine.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Fact(
                label = stringResource(R.string.routine_fact_exercises),
                value = routine.exercises.size.toString(),
                modifier = Modifier.weight(1f),
            )
            Fact(
                label = stringResource(R.string.routine_fact_sets),
                value = routine.exercises.sumOf { it.target.sets }.toString(),
                modifier = Modifier.weight(1f),
            )
            Fact(
                label = stringResource(R.string.routine_fact_last_done),
                value = when (lastDone) {
                    null -> stringResource(R.string.routine_fact_never)
                    else -> relativeDayText(lastDone, state.today) ?: workoutDateText(lastDone, state.today)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One exercise in order: its place, its name, and the target each set starts from. */
@Composable
private fun RoutineExerciseRow(number: Int, item: RoutineExercise, weightUnit: WeightUnit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.routine_exercise_click_label), onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(Spacing.xl)
                .alignBy(FirstBaseline),
        )
        Column(Modifier.weight(1f).alignBy(FirstBaseline)) {
            Text(
                text = item.exercise.name,
                style = MaterialTheme.typography.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xxs))
            SetTargetText(
                target = item.target,
                type = item.exercise.type,
                weightUnit = weightUnit,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.routine_detail_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xs).fillMaxWidth(0.5f).height(Spacing.lg))
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) {
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.sm))
                        Spacer(Modifier.height(Spacing.xs))
                        SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(Spacing.md))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            SkeletonRowWidths.forEach { (nameWidth, targetWidth) ->
                Column(Modifier.padding(start = Spacing.xl, top = Spacing.sm, bottom = Spacing.sm)) {
                    SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(nameWidth).height(Spacing.md))
                    SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(targetWidth).height(Spacing.sm))
                }
            }
        }
    }
}

private val SkeletonRowWidths = listOf(0.62f to 0.3f, 0.7f to 0.26f, 0.54f to 0.32f, 0.66f to 0.28f, 0.5f to 0.24f)

private const val CONTENT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun RoutineDetailPreview() {
    LiftBookTheme {
        RoutineDetailScreen(
            state = RoutineDetailUiState(
                isLoading = false,
                routine = RoutinePreviewData.push,
                today = RoutinePreviewData.today,
                zone = RoutinePreviewData.zone,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun RoutineDetailNeverDonePreview() {
    LiftBookTheme {
        RoutineDetailScreen(
            state = RoutineDetailUiState(
                isLoading = false,
                routine = RoutinePreviewData.legs,
                weightUnit = WeightUnit.LB,
                today = RoutinePreviewData.today,
                zone = RoutinePreviewData.zone,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
            otherWorkoutName = "Push",
        )
    }
}

@ThemePreviews
@Composable
private fun RoutineDetailLoadingPreview() {
    LiftBookTheme {
        RoutineDetailScreen(
            state = RoutineDetailUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
