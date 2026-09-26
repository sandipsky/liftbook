package com.example.liftbook.ui.feature.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.summarize
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.LoggedExerciseCard
import com.example.liftbook.ui.components.PersonalRecordsBlock
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.WorkoutStatsRow
import com.example.liftbook.ui.components.recapExercises
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.feature.workout.WorkoutPreviewData
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.LocalDate

@Composable
fun WorkoutDetailRoute(
    workoutId: String,
    onNavigateUp: () -> Unit,
    onEdit: (workoutId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel = hiltViewModel<WorkoutDetailViewModel, WorkoutDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(workoutId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnDeleted by rememberUpdatedState(onDeleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutDetailEvent.Deleted -> currentOnDeleted()
                WorkoutDetailEvent.DeleteFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.workout_detail_delete_failed))
            }
        }
    }

    WorkoutDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                WorkoutDetailAction.NavigateUp -> onNavigateUp()
                WorkoutDetailAction.Edit -> onEdit(workoutId)
                is WorkoutDetailAction.OpenExercise -> onOpenExercise(action.exerciseId)
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * A past workout, read back (FR-4.2): when it was and how long it took, the three numbers it came
 * to, any records it set, and every set, exercise by exercise. Edit is in the top bar, Delete
 * behind the menu — it can't be undone, so it's a step further away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onAction: (WorkoutDetailAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headerScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val workout = state.workout

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = workout?.name.orEmpty(),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(WorkoutDetailAction.NavigateUp) },
                showTitle = headerScrolledAway,
                actions = {
                    if (workout != null && !state.isDeleting) {
                        IconButton(onClick = { onAction(WorkoutDetailAction.Edit) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.workout_detail_edit))
                        }
                        DetailMenu(onDelete = { confirmDelete = true })
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = when {
            state.isLoading -> DetailContent.Loading
            workout == null || state.summary == null -> DetailContent.NotFound
            else -> DetailContent.Workout
        }
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "workoutDetailContent",
        ) { target ->
            when (target) {
                DetailContent.Loading -> DetailSkeleton(Modifier.padding(padding))
                DetailContent.NotFound -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.workout_detail_not_found_title),
                    body = stringResource(R.string.workout_detail_not_found_body),
                    modifier = Modifier.padding(padding),
                    action = {
                        OutlinedButton(onClick = { onAction(WorkoutDetailAction.NavigateUp) }) {
                            Text(stringResource(R.string.workout_detail_not_found_action))
                        }
                    },
                )
                DetailContent.Workout -> if (workout != null && state.summary != null) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + Spacing.xl,
                        ),
                    ) {
                        item(key = "header", contentType = "header") { DetailHeader(workout, state) }
                        if (state.summary.records.isNotEmpty()) {
                            item(key = "recordsHeader", contentType = "sectionHeader") {
                                SectionHeader(
                                    title = stringResource(R.string.workout_detail_records),
                                    trailing = state.summary.records.sumOf { it.records.size }.toString(),
                                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                                )
                            }
                            item(key = "records", contentType = "records") {
                                PersonalRecordsBlock(
                                    records = state.summary.records,
                                    weightUnit = state.weightUnit,
                                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                                )
                            }
                        }
                        item(key = "exercisesHeader", contentType = "sectionHeader") {
                            SectionHeader(
                                title = stringResource(R.string.workout_detail_exercises),
                                trailing = state.exercises.size.takeIf { it > 0 }?.toString(),
                                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                            )
                        }
                        if (state.exercises.isEmpty()) {
                            item(key = "noSets", contentType = "empty") {
                                Text(
                                    text = stringResource(R.string.workout_detail_no_sets),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                                )
                            }
                        }
                        items(state.exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
                            LoggedExerciseCard(
                                exercise = exercise,
                                weightUnit = state.weightUnit,
                                onClick = { onAction(WorkoutDetailAction.OpenExercise(exercise.exercise.id)) },
                                modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xs),
                            )
                        }
                        workout.note?.let { note ->
                            item(key = "note", contentType = "note") {
                                Column {
                                    SectionHeader(
                                        title = stringResource(R.string.workout_detail_note),
                                        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                                    )
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = Spacing.gutter),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && workout != null) {
        val sets = state.summary?.completedSets ?: 0
        ConfirmDialog(
            title = stringResource(R.string.workout_detail_delete_title, workout.name),
            text = if (sets > 0) {
                pluralStringResource(R.plurals.workout_detail_delete_body, sets, sets)
            } else {
                stringResource(R.string.workout_detail_delete_body_empty)
            },
            confirmLabel = stringResource(R.string.workout_detail_delete_confirm),
            dismissLabel = stringResource(R.string.workout_detail_delete_dismiss),
            onConfirm = {
                confirmDelete = false
                onAction(WorkoutDetailAction.Delete)
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

private enum class DetailContent { Loading, NotFound, Workout }

@Composable
private fun DetailMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_detail_delete)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DetailHeader(workout: Workout, state: WorkoutDetailUiState) {
    val summary = state.summary ?: return
    val start = workout.startedAt.atZone(state.zone)
    val end = (workout.finishedAt ?: workout.startedAt).atZone(state.zone)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs),
    ) {
        Text(
            text = workout.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                R.string.summary_when,
                workoutDateText(start.toLocalDate(), state.today),
                timeOfDayText(start.toLocalTime()),
                timeOfDayText(end.toLocalTime()),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        WorkoutStatsRow(summary = summary, weightUnit = state.weightUnit)
    }
}

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.workout_detail_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xs).fillMaxWidth(0.5f).height(Spacing.lg))
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.6f).height(Spacing.sm))
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) {
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.sm))
                        Spacer(Modifier.height(Spacing.xs))
                        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(Spacing.xl))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            repeat(3) {
                SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl * 2), MaterialTheme.shapes.large)
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

private const val CONTENT_FADE_MILLIS = 200

private fun previewState(): WorkoutDetailUiState {
    val finished = WorkoutPreviewData.workout.let { workout ->
        workout.copy(
            startedAt = workout.startedAt.minusSeconds(3 * 24 * 3_600),
            finishedAt = workout.startedAt.minusSeconds(3 * 24 * 3_600 - (58 * 60 + 40)),
            rest = null,
            note = "Paused the first rep of every bench set.",
            exercises = workout.exercises.map { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(isCompleted = true) }) },
        )
    }
    val previous = mapOf("bench" to listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(77.5, 8))))
    return WorkoutDetailUiState(
        isLoading = false,
        workout = finished,
        summary = finished.summarize(previous),
        exercises = finished.recapExercises(),
        today = LocalDate.of(2026, 9, 25),
        zone = WorkoutPreviewData.zone,
    )
}

@ThemePreviews
@Composable
private fun WorkoutDetailPreview() {
    LiftBookTheme {
        WorkoutDetailScreen(state = previewState(), onAction = {})
    }
}

@ThemePreviews
@Composable
private fun WorkoutDetailNotFoundPreview() {
    LiftBookTheme {
        WorkoutDetailScreen(state = WorkoutDetailUiState(isLoading = false), onAction = {})
    }
}

@ThemePreviews
@Composable
private fun WorkoutDetailLoadingPreview() {
    LiftBookTheme {
        WorkoutDetailScreen(state = WorkoutDetailUiState(), onAction = {})
    }
}
