package com.example.liftbook.ui.feature.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDecimal
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.ChartPoint
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.MetricValueText
import com.example.liftbook.ui.components.ProgressChart
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.bodyWeightChangeText
import com.example.liftbook.ui.components.bodyWeightChartValue
import com.example.liftbook.ui.components.bodyWeightText
import com.example.liftbook.ui.components.displayWeight
import com.example.liftbook.ui.components.shortDateText
import com.example.liftbook.ui.components.spokenRes
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

@Composable
fun BodyWeightRoute(
    onNavigateUp: () -> Unit,
    viewModel: BodyWeightViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BodyWeightLogEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.body_weight_deleted),
                        actionLabel = resources.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onAction(BodyWeightAction.Restore(event.entry))
                }
                BodyWeightLogEvent.SaveFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.body_weight_save_failed))
            }
        }
    }

    BodyWeightScreen(
        state = state,
        logWeight = viewModel.logWeight,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                BodyWeightAction.NavigateUp -> onNavigateUp()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Body weight over time (FR-5.4): the latest weigh-in, which way the trend is going, and the
 * chart — each weigh-in a quiet dot, the week-long trend the line through them, since a single
 * day's reading is mostly water. Log weight is the one primary action, held at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightScreen(
    state: BodyWeightUiState,
    logWeight: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (BodyWeightAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.body_weight_title),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(BodyWeightAction.NavigateUp) },
                showTitle = headlineScrolledAway,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = { BottomActionBar(text = stringResource(R.string.body_weight_log_action), onClick = { onAction(BodyWeightAction.Log) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = state.isLoading,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "bodyWeightContent",
        ) { loading ->
            if (loading) {
                SkeletonContainer(
                    contentDescription = stringResource(R.string.body_weight_loading),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                ) {
                    Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
                        SkeletonBlock(Modifier.padding(vertical = Spacing.xs).fillMaxWidth(0.5f).height(Spacing.lg))
                        Spacer(Modifier.height(Spacing.lg))
                        ChartSkeleton()
                    }
                }
            } else {
                BodyWeightList(state, listState, padding, onAction)
            }
        }
    }

    state.log?.let { log ->
        BodyWeightLogSheet(
            state = log,
            weight = logWeight,
            weightUnit = state.weightUnit,
            today = state.today,
            onChangeDate = { onAction(BodyWeightAction.ChangeLogDate(it)) },
            onSave = { onAction(BodyWeightAction.SaveLog) },
            onDelete = { onAction(BodyWeightAction.DeleteLogged) },
            onDismiss = { onAction(BodyWeightAction.DismissLog) },
        )
    }
}

@Composable
private fun BodyWeightList(state: BodyWeightUiState, listState: LazyListState, padding: PaddingValues, onAction: (BodyWeightAction) -> Unit) {
    var selected by remember(state.range, state.entries) { mutableStateOf<Int?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.lg),
    ) {
        item(key = "header", contentType = "header") {
            Text(
                text = stringResource(R.string.body_weight_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = Spacing.xs, bottom = Spacing.lg)
                    .semantics { heading() },
            )
        }
        if (!state.hasEntries) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.Outlined.MonitorWeight,
                    title = stringResource(R.string.body_weight_empty_title),
                    body = stringResource(R.string.body_weight_empty_body),
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            return@LazyColumn
        }
        item(key = "chart", contentType = "chart") {
            BodyWeightChartSection(state = state, selected = selected, onSelect = { selected = it }, onAction = onAction)
        }
        item(key = "range", contentType = "range") {
            RangeSelector(
                range = state.range,
                onSelect = { onAction(BodyWeightAction.SelectRange(it)) },
                modifier = Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.md),
            )
        }
        if (state.entries.isNotEmpty()) {
            item(key = "entriesHeader", contentType = "sectionHeader") {
                SectionHeader(
                    title = stringResource(R.string.body_weight_entries),
                    trailing = pluralStringResource(R.plurals.body_weight_entries_count, state.entries.size, state.entries.size),
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                )
            }
            items(state.entries.asReversed(), key = { it.id }, contentType = { "entry" }) { entry ->
                val weight = bodyWeightText(entry.weightKg, state.weightUnit)
                ProgressRow(
                    title = workoutDateText(entry.date, state.today),
                    subtitle = null,
                    value = MetricValueText(
                        number = displayWeight(entry.weightKg, state.weightUnit, maxFractionDigits = 1),
                        unit = stringResource(state.weightUnit.weightLabelRes()),
                        spoken = weight,
                    ),
                    onClick = { onAction(BodyWeightAction.Edit(entry)) },
                    clickLabel = stringResource(R.string.body_weight_edit_click_label),
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun BodyWeightChartSection(
    state: BodyWeightUiState,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onAction: (BodyWeightAction) -> Unit,
) {
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    val entries = state.entries
    if (entries.isEmpty()) {
        ChartEmptyPanel(
            title = stringResource(R.string.body_weight_range_empty_title, stringResource(state.range.spokenRes())),
            body = stringResource(R.string.body_weight_range_empty_body),
            actionLabel = stringResource(R.string.progress_show_all),
            onAction = { onAction(BodyWeightAction.SelectRange(ProgressRange.ALL)) },
            modifier = gutter,
        )
        return
    }
    val shown = selected?.let(entries::getOrNull)
    val latest = entries.last()
    val caption = if (shown != null) {
        workoutDateText(shown.date, state.today)
    } else {
        stringResource(R.string.progress_caption_latest, workoutDateText(latest.date, state.today))
    }
    val change = state.change?.let { change ->
        val amount = bodyWeightChangeText(change, state.weightUnit)
        val since = shortDateText(state.trend.first().date, state.today)
        MetricValueText(
            number = stringResource(R.string.body_weight_trend_since, amount.number, since),
            unit = null,
            spoken = stringResource(R.string.body_weight_trend_since, amount.spoken, since),
        )
    }
    val weight = shown ?: latest
    ChartHeadline(
        caption = caption,
        value = MetricValueText(
            number = displayWeight(weight.weightKg, state.weightUnit, maxFractionDigits = 1),
            unit = stringResource(state.weightUnit.weightLabelRes()),
            spoken = bodyWeightText(weight.weightKg, state.weightUnit),
        ),
        change = change,
        direction = directionOf(state.change ?: 0.0, isVisible = state.change != null),
        showChange = shown == null,
        modifier = gutter.padding(bottom = Spacing.lg),
    )
    val points = remember(entries, state.weightUnit) { entries.map { ChartPoint.on(it.date, bodyWeightChartValue(it.weightKg, state.weightUnit)) } }
    val trend = remember(state.trend, state.weightUnit) { state.trend.map { ChartPoint.on(it.date, bodyWeightChartValue(it.weightKg, state.weightUnit)) } }
    val first = bodyWeightText(entries.first().weightKg, state.weightUnit)
    val last = bodyWeightText(latest.weightKg, state.weightUnit)
    ProgressChart(
        points = points,
        trend = trend,
        axisLabel = { formatDecimal(it, maxFractionDigits = 1) },
        startLabel = shortDateText(entries.first().date, state.today),
        endLabel = shortDateText(latest.date, state.today),
        contentDescription = pluralStringResource(
            R.plurals.body_weight_chart_spoken,
            entries.size,
            entries.size,
            stringResource(state.range.spokenRes()),
            first,
            last,
        ),
        selectedIndex = selected,
        onSelect = onSelect,
        modifier = gutter,
    )
    ChartLegend(modifier = gutter.padding(top = Spacing.sm))
}

/** What the dots and the line are, so neither rests on its colour. */
@Composable
private fun ChartLegend(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.size(LegendDot).clip(CircleShape).background(colors.onSurfaceVariant))
        Text(stringResource(R.string.body_weight_legend_entries), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.xs))
        Box(Modifier.size(width = LegendLine, height = LegendStroke).clip(CircleShape).background(colors.primary))
        Text(stringResource(R.string.body_weight_legend_trend), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

private val LegendDot = 6.dp
private val LegendLine = Spacing.md
private val LegendStroke = 2.dp
private const val CONTENT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun BodyWeightScreenPreview() {
    LiftBookTheme {
        BodyWeightScreen(
            state = ProgressPreviewData.bodyWeight(),
            logWeight = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun BodyWeightEmptyPreview() {
    LiftBookTheme {
        BodyWeightScreen(
            state = BodyWeightUiState(isLoading = false, today = ProgressPreviewData.today),
            logWeight = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
