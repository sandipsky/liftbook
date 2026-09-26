package com.example.liftbook.ui.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.TimeOfDay
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.WorkoutInProgressDialog
import com.example.liftbook.ui.components.routineMetaText
import com.example.liftbook.ui.components.workoutNameRes
import com.example.liftbook.ui.feature.routines.RoutinePreviewData
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun HomeRoute(
    onOpenRoutine: (routineId: String) -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var otherWorkoutName by rememberSaveable { mutableStateOf<String?>(null) }
    val currentOnOpenWorkout by rememberUpdatedState(onOpenWorkout)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.OpenWorkout -> currentOnOpenWorkout()
                is HomeEvent.OtherWorkoutActive -> otherWorkoutName = event.workoutName
                HomeEvent.StartFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.start_failed))
                HomeEvent.RoutineMissing -> snackbarHostState.showSnackbar(resources.getString(R.string.start_routine_missing))
            }
        }
    }

    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        otherWorkoutName = otherWorkoutName,
        onAction = { action ->
            when (action) {
                is HomeAction.OpenRoutine -> onOpenRoutine(action.routineId)
                HomeAction.CreateRoutine -> onCreateRoutine()
                HomeAction.OpenSettings -> onOpenSettings()
                HomeAction.ResumeWorkout -> {
                    otherWorkoutName = null
                    onOpenWorkout()
                }
                HomeAction.DismissOtherWorkout -> otherWorkoutName = null
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (HomeAction) -> Unit,
    /** Set while the "another workout is in progress" dialog is up. */
    otherWorkoutName: String? = null,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val resources = LocalResources.current
    val content = if (state.isLoading) HomeContent.Loading else HomeContent.Loaded
    val startEmpty = {
        // Named for when it starts, in the user's language: "Evening workout".
        val name = resources.getString(TimeOfDay.of(LocalTime.now(state.zone)).workoutNameRes())
        onAction(HomeAction.StartEmpty(name))
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    // An empty list carries its own create action.
                    if (content == HomeContent.Loaded && state.routines.isNotEmpty()) {
                        IconButton(onClick = { onAction(HomeAction.CreateRoutine) }) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_new_routine))
                        }
                    }
                    IconButton(onClick = { onAction(HomeAction.OpenSettings) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_open))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "homeContent",
        ) { target ->
            when (target) {
                HomeContent.Loading -> HomeSkeleton(Modifier.padding(padding))
                HomeContent.Loaded -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + Spacing.xs,
                        bottom = padding.calculateBottomPadding() + Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    item(key = "startEmpty", contentType = "startEmpty") {
                        StartEmptyButton(
                            onClick = startEmpty,
                            modifier = Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.lg),
                        )
                    }
                    if (state.routines.isEmpty()) {
                        item(key = "empty", contentType = "empty") {
                            EmptyState(
                                icon = Icons.AutoMirrored.Outlined.ListAlt,
                                title = stringResource(R.string.home_empty_title),
                                body = stringResource(R.string.home_empty_body),
                                modifier = Modifier.padding(top = Spacing.xs),
                                action = {
                                    Button(onClick = { onAction(HomeAction.CreateRoutine) }) {
                                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
                                        Spacer(Modifier.size(Spacing.xs))
                                        Text(stringResource(R.string.home_empty_action))
                                    }
                                },
                            )
                        }
                    } else {
                        item(key = "header", contentType = "header") {
                            SectionHeader(
                                title = stringResource(R.string.home_routines),
                                trailing = state.routines.size.toString(),
                                modifier = Modifier.padding(bottom = Spacing.xxs),
                            )
                        }
                    }
                    items(state.routines, key = { it.id }, contentType = { "routine" }) { routine ->
                        RoutineCard(
                            routine = routine,
                            today = state.today,
                            zone = state.zone,
                            onOpen = { onAction(HomeAction.OpenRoutine(routine.id)) },
                            onStart = { onAction(HomeAction.StartRoutine(routine.id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    otherWorkoutName?.let { name ->
        WorkoutInProgressDialog(
            workoutName = name,
            onResume = { onAction(HomeAction.ResumeWorkout) },
            onDismiss = { onAction(HomeAction.DismissOtherWorkout) },
        )
    }
}

private enum class HomeContent { Loading, Loaded }

/**
 * Starts a workout with nothing planned (FR-3.1): exercises are added as they're done. It
 * carries the accent's soft tone, like each routine's start button — starting is what this tab
 * is for — and sits first, where it's found without scrolling.
 */
@Composable
private fun StartEmptyButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = StartEmptyHeight),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.action))
        Spacer(Modifier.size(Spacing.xs))
        Text(stringResource(R.string.home_start_empty), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A routine in the list: its name, what's in it, and when it was last done (FR-2.4) — enough to
 * pick today's. The card opens the routine; the play button starts it in one tap (FR-2.3), and
 * carries the accent's soft tone because starting is what this screen is for.
 */
@Composable
private fun RoutineCard(
    routine: Routine,
    today: LocalDate,
    zone: ZoneId,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.list_separator)
    val exerciseNames = remember(routine.exercises) { routine.exercises.joinToString(separator) { it.exercise.name } }
    Row(
        modifier = modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.routine_card_click_label), onClick = onOpen)
            .padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (exerciseNames.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = exerciseNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = routineMetaText(routine, today, zone),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (routine.exercises.isNotEmpty()) {
            FilledTonalIconButton(
                onClick = onStart,
                modifier = Modifier.size(StartButtonSize),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(R.string.routine_start_description, routine.name),
                    modifier = Modifier.size(IconSize.action),
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.home_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SkeletonBlock(Modifier.padding(bottom = Spacing.lg).fillMaxWidth().height(StartEmptyHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            repeat(3) {
                SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonCardHeight), MaterialTheme.shapes.large)
            }
        }
    }
}

private val StartButtonSize = 48.dp
private val StartEmptyHeight = 56.dp
private val SkeletonCardHeight = Spacing.xxl + Spacing.xxl + Spacing.md

private const val CONTENT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun HomeScreenPreview() {
    LiftBookTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                routines = RoutinePreviewData.routines,
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
private fun HomeScreenEmptyPreview() {
    LiftBookTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun HomeScreenLoadingPreview() {
    LiftBookTheme {
        HomeScreen(
            state = HomeUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
