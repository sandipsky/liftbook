package com.example.liftbook.ui.feature.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ProgressPoint
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.ui.components.ChartPoint
import com.example.liftbook.ui.components.ChoiceChip
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.ProgressChart
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.axisLabel
import com.example.liftbook.ui.components.chartValue
import com.example.liftbook.ui.components.exerciseMetaSpoken
import com.example.liftbook.ui.components.exerciseMetaText
import com.example.liftbook.ui.components.labelRes
import com.example.liftbook.ui.components.metricChangeText
import com.example.liftbook.ui.components.metricValueText
import com.example.liftbook.ui.components.shortDateText
import com.example.liftbook.ui.components.spokenLabelRes
import com.example.liftbook.ui.components.spokenRes
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

@Composable
fun ExerciseProgressRoute(
    exerciseId: String,
    onNavigateUp: () -> Unit,
    onOpenWorkout: (workoutId: String) -> Unit,
) {
    val viewModel = hiltViewModel<ExerciseProgressViewModel, ExerciseProgressViewModel.Factory>(
        creationCallback = { factory -> factory.create(exerciseId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseProgressScreen(
        state = state,
        onAction = { action ->
            when (action) {
                ExerciseProgressAction.NavigateUp -> onNavigateUp()
                is ExerciseProgressAction.OpenWorkout -> onOpenWorkout(action.workoutId)
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * One exercise's progress (FR-5.1): the metric it's judged by — best estimated 1RM, heaviest
 * set, volume, or what its type records — as a number and a line over the chosen range, then
 * the workouts behind the line, newest first, each opening its workout. The range sits under
 * the chart, in reach of the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(state: ExerciseProgressUiState, onAction: (ExerciseProgressAction) -> Unit) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val exercise = state.exercise
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = exercise?.name.orEmpty(),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(ExerciseProgressAction.NavigateUp) },
                showTitle = headlineScrolledAway,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = when {
            state.isLoading -> Content.Loading
            exercise == null -> Content.NotFound
            else -> Content.Loaded
        }
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "exerciseProgressContent",
        ) { target ->
            when (target) {
                Content.Loading -> ProgressSkeleton(Modifier.padding(padding))
                Content.NotFound -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.exercise_detail_not_found_title),
                    body = stringResource(R.string.exercise_detail_not_found_body),
                    modifier = Modifier.padding(padding),
                    action = {
                        OutlinedButton(onClick = { onAction(ExerciseProgressAction.NavigateUp) }) {
                            Text(stringResource(R.string.exercise_detail_not_found_action))
                        }
                    },
                )
                Content.Loaded -> if (exercise != null) {
                    ProgressList(state, exercise, listState, padding, onAction)
                }
            }
        }
    }
}

private enum class Content { Loading, NotFound, Loaded }

@Composable
private fun ProgressList(
    state: ExerciseProgressUiState,
    exercise: Exercise,
    listState: LazyListState,
    padding: PaddingValues,
    onAction: (ExerciseProgressAction) -> Unit,
) {
    // The point being scrubbed; a new metric or range starts from the latest again.
    var selected by remember(state.metric, state.range, state.points) { mutableStateOf<Int?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xl),
    ) {
        item(key = "header", contentType = "header") { Header(exercise) }
        if (!state.hasHistory) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = stringResource(R.string.exercise_progress_empty_title),
                    body = stringResource(R.string.exercise_progress_empty_body),
                )
            }
            return@LazyColumn
        }
        if (state.metrics.size > 1) {
            item(key = "metrics", contentType = "metrics") {
                FlowRow(
                    modifier = Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.lg).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    state.metrics.forEach { metric ->
                        ChoiceChip(
                            selected = metric == state.metric,
                            onClick = { onAction(ExerciseProgressAction.SelectMetric(metric)) },
                            label = stringResource(metric.labelRes()),
                        )
                    }
                }
            }
        }
        item(key = "chart", contentType = "chart") {
            ChartSection(state = state, selected = selected, onSelect = { selected = it }, onAction = onAction)
        }
        item(key = "range", contentType = "range") {
            RangeSelector(
                range = state.range,
                onSelect = { onAction(ExerciseProgressAction.SelectRange(it)) },
                modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md),
            )
        }
        if (state.points.isNotEmpty()) {
            item(key = "workoutsHeader", contentType = "sectionHeader") {
                SectionHeader(
                    title = stringResource(R.string.exercise_progress_workouts),
                    trailing = pluralStringResource(R.plurals.workouts_count, state.points.size, state.points.size),
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                )
            }
            items(state.points.asReversed(), key = { it.workoutId }, contentType = { "workout" }) { point ->
                ProgressRow(
                    title = workoutDateText(point.date, state.today),
                    subtitle = point.workoutName,
                    value = metricValueText(state.metric, point.value, state.weightUnit),
                    onClick = { onAction(ExerciseProgressAction.OpenWorkout(point.workoutId)) },
                    clickLabel = stringResource(R.string.history_workout_click_label),
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun Header(exercise: Exercise) {
    val metaSpoken = exerciseMetaSpoken(exercise)
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.xs, bottom = Spacing.lg)) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = exerciseMetaText(exercise),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = metaSpoken },
        )
    }
}

/** The headline number and the chart — or, with nothing in range, what to do about it. */
@Composable
private fun ChartSection(
    state: ExerciseProgressUiState,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onAction: (ExerciseProgressAction) -> Unit,
) {
    val summary = state.summary
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    if (summary == null) {
        val rangeName = stringResource(state.range.spokenRes())
        if (state.metricHasValues) {
            ChartEmptyPanel(
                title = stringResource(R.string.progress_range_empty_title, rangeName),
                body = stringResource(R.string.progress_range_empty_body),
                actionLabel = stringResource(R.string.progress_show_all),
                onAction = { onAction(ExerciseProgressAction.SelectRange(ProgressRange.ALL)) },
                modifier = gutter,
            )
        } else {
            ChartEmptyPanel(
                title = stringResource(R.string.progress_metric_empty_title),
                body = stringResource(R.string.progress_metric_empty_body),
                modifier = gutter,
            )
        }
        return
    }
    val points = state.points
    val shown = selected?.let(points::getOrNull)
    val latest = summary.latest
    val metricName = stringResource(state.metric.spokenLabelRes())
    val caption = if (shown != null) {
        stringResource(R.string.progress_caption_point, workoutDateText(shown.date, state.today), shown.workoutName)
    } else {
        stringResource(R.string.progress_caption_latest, workoutDateText(latest.date, state.today))
    }
    val change = if (points.size >= 2) {
        val amount = metricChangeText(state.metric, summary.change, state.weightUnit)
        val since = shortDateText(summary.first.date, state.today)
        amount.copy(
            number = stringResource(R.string.progress_change_since, amount.number, since),
            spoken = stringResource(R.string.progress_change_since, amount.spoken, since),
        )
    } else {
        null
    }
    ChartHeadline(
        caption = caption,
        value = metricValueText(state.metric, (shown ?: latest).value, state.weightUnit),
        change = change,
        direction = directionOf(summary.change, isVisible = points.size >= 2),
        showChange = shown == null,
        modifier = gutter.padding(bottom = Spacing.lg),
    )
    val chartPoints = remember(points, state.metric, state.weightUnit, state.zone) {
        points.map { ChartPoint.at(it.startedAt, state.zone, state.metric.chartValue(it.value, state.weightUnit)) }
    }
    ProgressChart(
        points = chartPoints,
        axisLabel = { state.metric.axisLabel(it) },
        startLabel = shortDateText(points.first().date, state.today),
        endLabel = shortDateText(points.last().date, state.today),
        contentDescription = chartDescription(state, metricName, points),
        selectedIndex = selected,
        onSelect = onSelect,
        modifier = gutter,
    )
}

/** "Estimated one-rep max chart, 3 months: 12 workouts, from 100 kg to 116.7 kg. Best 116.7 kg." */
@Composable
private fun chartDescription(state: ExerciseProgressUiState, metricName: String, points: List<ProgressPoint>): String {
    val range = stringResource(state.range.spokenRes())
    val first = metricValueText(state.metric, points.first().value, state.weightUnit).spoken
    if (points.size == 1) return stringResource(R.string.progress_chart_spoken_single, metricName, range, first)
    val latest = metricValueText(state.metric, points.last().value, state.weightUnit).spoken
    val best = metricValueText(state.metric, points.maxOf { it.value }, state.weightUnit).spoken
    return pluralStringResource(R.plurals.progress_chart_spoken, points.size, points.size, metricName, range, first, latest, best)
}

@Composable
private fun ProgressSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.progress_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xs).fillMaxWidth(0.7f).height(Spacing.lg))
            Spacer(Modifier.height(Spacing.lg))
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.xl), MaterialTheme.shapes.large)
            Spacer(Modifier.height(Spacing.lg))
            ChartSkeleton()
        }
    }
}

private const val CONTENT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun ExerciseProgressPreview() {
    LiftBookTheme {
        ExerciseProgressScreen(state = ProgressPreviewData.benchProgress(), onAction = {})
    }
}

@ThemePreviews
@Composable
private fun ExerciseProgressEmptyRangePreview() {
    LiftBookTheme {
        ExerciseProgressScreen(
            state = ProgressPreviewData.benchProgress().copy(range = ProgressRange.ONE_MONTH, points = emptyList(), summary = null),
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ExerciseProgressNoHistoryPreview() {
    LiftBookTheme {
        ExerciseProgressScreen(
            state = ProgressPreviewData.benchProgress().copy(points = emptyList(), summary = null, hasHistory = false, metricHasValues = false),
            onAction = {},
        )
    }
}
