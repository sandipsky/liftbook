package com.example.liftbook.ui.feature.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.liftbook.R
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.StatTile
import com.example.liftbook.ui.components.displayWeight
import com.example.liftbook.ui.components.hoursMinutesText
import com.example.liftbook.ui.components.monthText
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.components.workoutDurationSpoken
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HistoryRoute(
    onOpenWorkout: (workoutId: String) -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val workouts = viewModel.workouts.collectAsLazyPagingItems()
    HistoryScreen(
        state = state,
        workouts = workouts,
        onAction = { action ->
            when (action) {
                is HistoryAction.OpenWorkout -> onOpenWorkout(action.workoutId)
                HistoryAction.StartWorkout -> onStartWorkout()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Everything done so far: every workout, newest first under its month (FR-4.1), or a month of
 * training days on a calendar (FR-4.4). The top bar swaps between the two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    workouts: LazyPagingItems<HistoryListItem>,
    onAction: (HistoryAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // Held here so the list keeps its place while the calendar shows.
    val listState = rememberLazyListState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    // Shows the other view, so the icon is what a tap brings.
                    when (state.view) {
                        HistoryView.List -> IconButton(onClick = { onAction(HistoryAction.ShowView(HistoryView.Calendar)) }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.history_show_calendar))
                        }
                        HistoryView.Calendar -> IconButton(onClick = { onAction(HistoryAction.ShowView(HistoryView.List)) }) {
                            Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = stringResource(R.string.history_show_list))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = state.view,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "historyView",
        ) { view ->
            when (view) {
                HistoryView.List -> HistoryList(state = state, workouts = workouts, listState = listState, padding = padding, onAction = onAction)
                HistoryView.Calendar -> CalendarView(state = state, padding = padding, onAction = onAction)
            }
        }
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    workouts: LazyPagingItems<HistoryListItem>,
    listState: LazyListState,
    padding: PaddingValues,
    onAction: (HistoryAction) -> Unit,
) {
    val refresh = workouts.loadState.refresh
    when {
        workouts.itemCount == 0 && refresh is LoadState.Loading -> ListSkeleton(Modifier.padding(padding))
        workouts.itemCount == 0 && refresh is LoadState.Error -> EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.history_error_title),
            body = stringResource(R.string.history_error_body),
            modifier = Modifier.padding(padding),
            action = {
                OutlinedButton(onClick = { workouts.retry() }) { Text(stringResource(R.string.history_retry)) }
            },
        )
        workouts.itemCount == 0 -> EmptyState(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.history_empty_title),
            body = stringResource(R.string.history_empty_body),
            modifier = Modifier.padding(padding),
            action = {
                Button(onClick = { onAction(HistoryAction.StartWorkout) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
                    Spacer(Modifier.size(Spacing.xs))
                    Text(stringResource(R.string.history_empty_action))
                }
            },
        )
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.lg,
            ),
        ) {
            items(
                count = workouts.itemCount,
                key = workouts.itemKey { it.key },
                contentType = workouts.itemContentType { if (it is HistoryListItem.Month) "month" else "workout" },
            ) { index ->
                when (val item = workouts[index]) {
                    is HistoryListItem.Month -> SectionHeader(
                        title = monthText(item.month, state.today),
                        modifier = Modifier
                            .animateItem()
                            .padding(top = if (index == 0) Spacing.xs else Spacing.lg, bottom = Spacing.xs),
                    )
                    is HistoryListItem.Workout -> WorkoutHistoryRow(
                        workout = item.workout,
                        today = state.today,
                        zone = state.zone,
                        weightUnit = state.weightUnit,
                        onClick = { onAction(HistoryAction.OpenWorkout(item.workout.id)) },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                    )
                    null -> Unit
                }
            }
            if (workouts.loadState.append is LoadState.Loading) {
                item(key = "appending", contentType = "skeleton") {
                    SkeletonContainer(
                        contentDescription = stringResource(R.string.history_loading),
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                    ) {
                        WorkoutHistoryRowSkeleton()
                    }
                }
            }
        }
    }
}

/**
 * A month on the calendar (FR-4.4): what it added up to, then its days. The grid sits in the
 * lower part of the screen, where a thumb reaches the days. A day with one workout opens it; a
 * day with more asks which.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarView(state: HistoryUiState, padding: PaddingValues, onAction: (HistoryAction) -> Unit) {
    val calendar = state.calendar
    if (calendar == null) {
        CalendarSkeleton(Modifier.padding(padding))
        return
    }
    var openDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding() + Spacing.xs, bottom = padding.calculateBottomPadding() + Spacing.lg),
    ) {
        CalendarMonthHeader(
            calendar = calendar,
            today = state.today,
            onPreviousMonth = { onAction(HistoryAction.PreviousMonth) },
            onNextMonth = { onAction(HistoryAction.NextMonth) },
        )
        MonthTotals(
            calendar = calendar,
            today = state.today,
            weightUnit = state.weightUnit,
            modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs, bottom = Spacing.lg),
        )
        TrainingCalendarGrid(
            calendar = calendar,
            today = state.today,
            onOpenDay = { date ->
                val workouts = calendar.days[date].orEmpty()
                when (workouts.size) {
                    0 -> Unit
                    1 -> onAction(HistoryAction.OpenWorkout(workouts.single().id))
                    else -> openDay = date
                }
            },
            onPreviousMonth = { onAction(HistoryAction.PreviousMonth) },
            onNextMonth = { onAction(HistoryAction.NextMonth) },
        )
    }

    openDay?.let { date ->
        val workouts = calendar.days[date].orEmpty()
        if (workouts.isEmpty()) {
            openDay = null
        } else {
            DaySheet(
                date = date,
                workouts = workouts,
                state = state,
                onOpen = { id ->
                    openDay = null
                    onAction(HistoryAction.OpenWorkout(id))
                },
                onDismiss = { openDay = null },
            )
        }
    }
}

/** The month in three numbers, or a line saying nothing was done in it. */
@Composable
private fun MonthTotals(calendar: CalendarMonth, today: LocalDate, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    val totals = calendar.totals
    if (totals.workouts == 0) {
        Text(
            text = if (calendar.month == YearMonth.from(today)) {
                stringResource(R.string.history_month_empty_current)
            } else {
                stringResource(R.string.history_month_empty, monthText(calendar.month, today))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatTile(
            label = stringResource(R.string.history_stat_workouts),
            value = totals.workouts.toString(),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.history_stat_time),
            value = hoursMinutesText(totals.durationSeconds),
            unit = stringResource(R.string.unit_hours),
            spokenValue = workoutDurationSpoken(totals.durationSeconds),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.history_stat_volume),
            value = displayWeight(totals.volumeKg, weightUnit, maxFractionDigits = 0),
            unit = stringResource(weightUnit.weightLabelRes()),
            modifier = Modifier.weight(1f),
        )
    }
}

/** A day with more than one workout: which to open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    date: LocalDate,
    workouts: List<WorkoutListItem>,
    state: HistoryUiState,
    onOpen: (workoutId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = workoutDateText(date, state.today),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter)
                    .padding(bottom = Spacing.xs)
                    .semantics { heading() },
            )
            workouts.forEach { workout ->
                WorkoutHistoryRow(
                    workout = workout,
                    today = state.today,
                    zone = state.zone,
                    weightUnit = state.weightUnit,
                    onClick = { onOpen(workout.id) },
                    // The sheet's title is the day; the time tells its workouts apart.
                    showDay = false,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }
        }
    }
}

@Composable
private fun ListSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.history_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            repeat(SKELETON_ROWS) { WorkoutHistoryRowSkeleton() }
        }
    }
}

@Composable
private fun CalendarSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.history_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(Spacing.lg))
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(3) {
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.sm))
                        Spacer(Modifier.height(Spacing.xs))
                        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(Spacing.lg))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl * 6), MaterialTheme.shapes.large)
        }
    }
}

private const val CONTENT_FADE_MILLIS = 200
private const val SKELETON_ROWS = 5

@Composable
private fun previewItems(items: List<HistoryListItem>): LazyPagingItems<HistoryListItem> =
    remember { MutableStateFlow(PagingData.from(items)) }.collectAsLazyPagingItems()

private fun previewState(view: HistoryView = HistoryView.List, calendar: CalendarMonth? = null) = HistoryUiState(
    view = view,
    calendar = calendar,
    today = HistoryPreviewData.today,
    zone = HistoryPreviewData.zone,
)

@ThemePreviews
@Composable
private fun HistoryListPreview() {
    LiftBookTheme {
        HistoryScreen(state = previewState(), workouts = previewItems(HistoryPreviewData.listItems), onAction = {})
    }
}

@ThemePreviews
@Composable
private fun HistoryCalendarPreview() {
    LiftBookTheme {
        HistoryScreen(
            state = previewState(HistoryView.Calendar, HistoryPreviewData.calendar()),
            workouts = previewItems(emptyList()),
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun HistoryCalendarEmptyMonthPreview() {
    LiftBookTheme {
        HistoryScreen(
            state = previewState(HistoryView.Calendar, HistoryPreviewData.calendar(YearMonth.of(2026, 6))),
            workouts = previewItems(emptyList()),
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun HistoryEmptyPreview() {
    LiftBookTheme {
        HistoryScreen(state = previewState(), workouts = previewItems(emptyList()), onAction = {})
    }
}
