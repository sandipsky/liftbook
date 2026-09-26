package com.example.liftbook.ui.feature.exercises

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.liftbook.R
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.Fact
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SetMetricsText
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.labelRes
import com.example.liftbook.ui.components.relativeDayText
import com.example.liftbook.ui.components.setMarker
import com.example.liftbook.ui.components.setSpokenTitle
import com.example.liftbook.ui.components.setTitle
import com.example.liftbook.ui.components.workingSetNumbers
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle
import com.example.liftbook.ui.theme.tabularNumbers
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ExerciseDetailRoute(
    exerciseId: String,
    onNavigateUp: () -> Unit,
    onEdit: (exerciseId: String) -> Unit,
    onArchived: (exerciseId: String) -> Unit,
) {
    val viewModel = hiltViewModel<ExerciseDetailViewModel, ExerciseDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(exerciseId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history = viewModel.history.collectAsLazyPagingItems()
    val currentOnArchived by rememberUpdatedState(onArchived)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExerciseDetailEvent.Archived -> currentOnArchived(event.exerciseId)
            }
        }
    }

    ExerciseDetailScreen(
        state = state,
        history = history,
        onAction = { action ->
            when (action) {
                ExerciseDetailAction.NavigateUp -> onNavigateUp()
                ExerciseDetailAction.Edit -> onEdit(exerciseId)
                else -> viewModel.onAction(action)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    history: LazyPagingItems<ExerciseSession>,
    onAction: (ExerciseDetailAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // The name moves into the top bar only once the headline has scrolled out of view.
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val exercise = state.exercise

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = exercise?.name.orEmpty(),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(ExerciseDetailAction.NavigateUp) },
                showTitle = headlineScrolledAway,
                actions = { if (exercise != null) DetailActions(exercise = exercise, onAction = onAction) },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = when {
            state.isLoading -> DetailContent.Loading
            exercise == null -> DetailContent.NotFound
            else -> DetailContent.Loaded
        }
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "detailContent",
        ) { target ->
            when (target) {
                DetailContent.Loading -> DetailSkeleton(Modifier.padding(padding))
                DetailContent.NotFound -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.exercise_detail_not_found_title),
                    body = stringResource(R.string.exercise_detail_not_found_body),
                    modifier = Modifier.padding(padding),
                    action = {
                        OutlinedButton(onClick = { onAction(ExerciseDetailAction.NavigateUp) }) {
                            Text(stringResource(R.string.exercise_detail_not_found_action))
                        }
                    },
                )
                DetailContent.Loaded -> if (exercise != null) {
                    DetailList(
                        state = state,
                        exercise = exercise,
                        history = history,
                        listState = listState,
                        padding = padding,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

private enum class DetailContent { Loading, NotFound, Loaded }

@Composable
private fun DetailActions(exercise: Exercise, onAction: (ExerciseDetailAction) -> Unit) {
    // An archived exercise offers Restore in the page itself instead.
    if (exercise.isArchived) return
    if (exercise.isEditable) {
        IconButton(onClick = { onAction(ExerciseDetailAction.Edit) }) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.exercise_detail_edit))
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercise_detail_archive)) },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onAction(ExerciseDetailAction.Archive)
                },
            )
        }
    }
}

@Composable
private fun DetailList(
    state: ExerciseDetailUiState,
    exercise: Exercise,
    history: LazyPagingItems<ExerciseSession>,
    listState: LazyListState,
    padding: PaddingValues,
    onAction: (ExerciseDetailAction) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + Spacing.xl,
        ),
    ) {
        item(key = "header", contentType = "header") { DetailHeader(exercise) }
        if (exercise.isArchived) {
            item(key = "archived", contentType = "archived") {
                ArchivedNotice(onRestore = { onAction(ExerciseDetailAction.Restore) })
            }
        }
        val lastSession = state.lastSession
        if (lastSession == null) {
            item(key = "noHistory", contentType = "empty") {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.exercise_detail_empty_title),
                    body = stringResource(R.string.exercise_detail_empty_body),
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        } else {
            item(key = "lastTime", contentType = "lastTime") { LastTimeSection(lastSession, state) }
            item(key = "historyHeader", contentType = "sectionHeader") {
                SectionHeader(
                    title = stringResource(R.string.exercise_detail_history),
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                )
            }
            if (history.itemCount == 0 && history.loadState.refresh is LoadState.Loading) {
                item(key = "historyLoading", contentType = "skeleton") { SessionSkeleton() }
            }
            items(
                count = history.itemCount,
                key = history.itemKey { it.workoutExerciseId },
                contentType = history.itemContentType { "session" },
            ) { index ->
                history[index]?.let { session ->
                    SessionCard(
                        session = session,
                        state = state,
                        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xs),
                    )
                }
            }
            if (history.loadState.append is LoadState.Loading) {
                item(key = "historyAppending", contentType = "skeleton") { SessionSkeleton() }
            }
        }
    }
}

@Composable
private fun DetailHeader(exercise: Exercise) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs, bottom = Spacing.lg),
    ) {
        if (exercise.isCustom) {
            Text(
                text = stringResource(R.string.exercise_detail_custom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
        }
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Fact(
                label = stringResource(R.string.exercise_fact_muscle),
                value = stringResource(exercise.primaryMuscle.labelRes()),
                modifier = Modifier.weight(1f),
            )
            Fact(
                label = stringResource(R.string.exercise_fact_equipment),
                value = stringResource(exercise.equipment.labelRes()),
                modifier = Modifier.weight(1f),
            )
            Fact(
                label = stringResource(R.string.exercise_fact_logged_as),
                value = stringResource(exercise.type.labelRes()),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ArchivedNotice(onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            Icons.Outlined.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.inline),
        )
        Text(
            text = stringResource(R.string.exercise_detail_archived_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) { Text(stringResource(R.string.action_restore)) }
    }
}

/** The last-performed values (FR-1.5): what the user lifted last time, at a glance. */
@Composable
private fun LastTimeSection(session: ExerciseSession, state: ExerciseDetailUiState) {
    val date = session.startedAt.atZone(state.zone).toLocalDate()
    val numbers = remember(session) { workingSetNumbers(session.sets) }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.exercise_detail_last_time),
            trailing = relativeDayText(date, state.today),
        )
        Text(
            text = stringResource(
                R.string.exercise_detail_session_caption,
                workoutDateText(date, state.today),
                session.workoutName,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        Spacer(Modifier.height(Spacing.md))
        FlowRow(
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            session.sets.forEachIndexed { index, set ->
                SetTile(set = set, number = numbers[index], weightUnit = state.weightUnit)
            }
        }
    }
}

@Composable
private fun SetTile(set: LoggedSet, number: Int?, weightUnit: WeightUnit) {
    val spokenTitle = setSpokenTitle(set.setType, number)
    // Warm-ups sit a tone quieter; their label says what they are, so tone isn't the only signal.
    val container = if (set.setType == SetType.WARMUP) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = setTitle(set.setType, number),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = spokenTitle },
        )
        SetMetricsText(metrics = set.metrics, weightUnit = weightUnit, style = MaterialTheme.typography.titleLarge)
    }
}

/** One past session in the history (FR-4.3): its date and workout, then every completed set. */
@Composable
private fun SessionCard(session: ExerciseSession, state: ExerciseDetailUiState, modifier: Modifier = Modifier) {
    val date = session.startedAt.atZone(state.zone).toLocalDate()
    val numbers = remember(session) { workingSetNumbers(session.sets) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.xxs)) {
            Text(
                text = workoutDateText(date, state.today),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = session.workoutName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().padding(start = Spacing.sm),
            )
        }
        session.sets.forEachIndexed { index, set ->
            SetLine(set = set, number = numbers[index], weightUnit = state.weightUnit)
        }
    }
}

@Composable
private fun SetLine(set: LoggedSet, number: Int?, weightUnit: WeightUnit) {
    val spokenTitle = setSpokenTitle(set.setType, number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = setMarker(set.setType, number),
            style = MaterialTheme.typography.labelLarge.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(Spacing.lg)
                .semantics { contentDescription = spokenTitle },
        )
        SetMetricsText(metrics = set.metrics, weightUnit = weightUnit)
    }
}

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.exercise_detail_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xs).fillMaxWidth(0.7f).height(Spacing.lg))
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) {
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.sm))
                        Spacer(Modifier.height(Spacing.xs))
                        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(Spacing.md))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(Spacing.md))
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                repeat(3) {
                    SkeletonBlock(Modifier.size(width = SkeletonTileWidth, height = Spacing.xxl), MaterialTheme.shapes.medium)
                }
            }
        }
    }
}

@Composable
private fun SessionSkeleton() {
    SkeletonContainer(
        contentDescription = stringResource(R.string.exercise_detail_loading),
        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xs),
    ) {
        SkeletonBlock(Modifier.fillMaxWidth().height(SessionSkeletonHeight), MaterialTheme.shapes.large)
    }
}

private val SkeletonTileWidth = 96.dp
private val SessionSkeletonHeight = Spacing.xxl + Spacing.xxl + Spacing.xl

private const val CONTENT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun ExerciseDetailScreenPreview() {
    LiftBookTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState(
                isLoading = false,
                exercise = ExercisePreviewData.benchPress,
                lastSession = ExercisePreviewData.benchSessions.first(),
                today = ExercisePreviewData.today,
                zone = ExercisePreviewData.zone,
            ),
            history = previewHistory(ExercisePreviewData.benchSessions),
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExerciseDetailNoHistoryPreview() {
    LiftBookTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState(
                isLoading = false,
                exercise = ExercisePreviewData.cableYRaise,
                today = ExercisePreviewData.today,
                zone = ExercisePreviewData.zone,
            ),
            history = previewHistory(emptyList()),
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExerciseDetailArchivedPreview() {
    LiftBookTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState(
                isLoading = false,
                exercise = ExercisePreviewData.archivedCurl,
                today = ExercisePreviewData.today,
                zone = ExercisePreviewData.zone,
            ),
            history = previewHistory(emptyList()),
            onAction = {},
        )
    }
}

/**
 * Paged items for previews. LazyPagingItems shows data synchronously only when its flow replays
 * a cached PagingData — as the ViewModel's cachedIn() flow does — so a StateFlow stands in for it.
 */
@Composable
private fun previewHistory(sessions: List<ExerciseSession>): LazyPagingItems<ExerciseSession> =
    remember { MutableStateFlow(PagingData.from(sessions)) }.collectAsLazyPagingItems()
