package com.example.liftbook.ui.feature.progress

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.model.TrainedExercise
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.ChartPoint
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.Sparkline
import com.example.liftbook.ui.components.bodyWeightChangeText
import com.example.liftbook.ui.components.bodyWeightChartValue
import com.example.liftbook.ui.components.bodyWeightText
import com.example.liftbook.ui.components.dateRangeText
import com.example.liftbook.ui.components.displayWeight
import com.example.liftbook.ui.components.relativeDayText
import com.example.liftbook.ui.components.shortDateText
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle
import com.example.liftbook.ui.theme.tabularNumbers

@Composable
fun ProgressRoute(
    onOpenExercise: (exerciseId: String) -> Unit,
    onOpenBodyWeight: () -> Unit,
    onStartWorkout: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BodyWeightLogEvent.SaveFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.body_weight_save_failed))
                // The tab never opens a weigh-in to delete it.
                is BodyWeightLogEvent.Deleted -> Unit
            }
        }
    }

    ProgressScreen(
        state = state,
        logWeight = viewModel.logWeight,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                is ProgressAction.OpenExercise -> onOpenExercise(action.exerciseId)
                ProgressAction.OpenBodyWeight -> onOpenBodyWeight()
                ProgressAction.StartWorkout -> onStartWorkout()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Progress at a glance (FR-5): this week against last — workouts, volume, sets and the sets each
 * muscle got (FR-5.3) — then body weight and its trend, with Log right there (FR-5.4), then every
 * exercise done, most recent first, each opening its charts (FR-5.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    state: ProgressUiState,
    logWeight: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (ProgressAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.progress_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
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
            targetState = state.isLoading,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "progressContent",
        ) { loading ->
            if (loading) {
                ProgressTabSkeleton(Modifier.padding(padding))
                return@AnimatedContent
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + Spacing.xs,
                    bottom = padding.calculateBottomPadding() + Spacing.lg,
                ),
            ) {
                val week = state.week
                if (state.hasNoWorkouts || week == null) {
                    item(key = "empty", contentType = "empty") {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.ShowChart,
                            title = stringResource(R.string.progress_empty_title),
                            body = stringResource(R.string.progress_empty_body),
                            modifier = Modifier.padding(bottom = Spacing.xs),
                            action = {
                                Button(onClick = { onAction(ProgressAction.StartWorkout) }) {
                                    Text(stringResource(R.string.progress_empty_action))
                                }
                            },
                        )
                    }
                } else {
                    item(key = "weekHeader", contentType = "sectionHeader") {
                        SectionHeader(
                            title = stringResource(R.string.week_title),
                            trailing = dateRangeText(week.current.start, week.current.end, state.today),
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                    item(key = "week", contentType = "week") {
                        WeeklySummaryBlock(week = week, weightUnit = state.weightUnit, modifier = Modifier.padding(horizontal = Spacing.gutter))
                    }
                }
                item(key = "bodyWeightHeader", contentType = "bodyWeightHeader") {
                    BodyWeightHeader(
                        onLog = { onAction(ProgressAction.LogBodyWeight) },
                        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs),
                    )
                }
                item(key = "bodyWeight", contentType = "bodyWeight") {
                    val glance = state.bodyWeight
                    if (glance == null) {
                        BodyWeightPrompt(onClick = { onAction(ProgressAction.LogBodyWeight) })
                    } else {
                        BodyWeightCard(glance = glance, state = state, onClick = { onAction(ProgressAction.OpenBodyWeight) })
                    }
                }
                if (state.exercises.isNotEmpty()) {
                    item(key = "exercisesHeader", contentType = "sectionHeader") {
                        SectionHeader(
                            title = stringResource(R.string.progress_exercises),
                            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                        )
                    }
                    items(state.exercises, key = { it.exercise.id }, contentType = { "exercise" }) { trained ->
                        TrainedExerciseRow(
                            trained = trained,
                            state = state,
                            onClick = { onAction(ProgressAction.OpenExercise(trained.exercise.id)) },
                            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }

    state.log?.let { log ->
        BodyWeightLogSheet(
            state = log,
            weight = logWeight,
            weightUnit = state.weightUnit,
            today = state.today,
            onChangeDate = { onAction(ProgressAction.ChangeLogDate(it)) },
            onSave = { onAction(ProgressAction.SaveLog) },
            onDelete = {},
            onDismiss = { onAction(ProgressAction.DismissLog) },
        )
    }
}

/** The section's title, with Log beside it: the section's one action, a tap away. */
@Composable
private fun BodyWeightHeader(onLog: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.body_weight_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onLog) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
            Spacer(Modifier.size(Spacing.xs))
            Text(stringResource(R.string.body_weight_log_short))
        }
    }
}

/** The latest weigh-in and where the trend is heading, opening the full log. */
@Composable
private fun BodyWeightCard(glance: BodyWeightGlance, state: ProgressUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val unit = state.weightUnit
    val latest = glance.latest
    val day = relativeDayText(latest.date, state.today) ?: workoutDateText(latest.date, state.today)
    val change = glance.change?.let { change ->
        val amount = bodyWeightChangeText(change, unit)
        val since = shortDateText(glance.trend.first().date, state.today)
        stringResource(R.string.body_weight_trend_since, amount.number, since) to stringResource(R.string.body_weight_trend_since, amount.spoken, since)
    }
    val weight = bodyWeightText(latest.weightKg, unit)
    val spoken = if (change == null) {
        stringResource(R.string.body_weight_card_spoken, weight, day)
    } else {
        stringResource(R.string.body_weight_card_spoken_trend, weight, day, change.second)
    }
    val trend = remember(glance.trend, unit) { glance.trend.map { ChartPoint.on(it.date, bodyWeightChartValue(it.weightKg, unit)) } }
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.body_weight_card_click_label), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Text(day, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = displayWeight(latest.weightKg, unit, maxFractionDigits = 1),
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                    color = colors.onSurface,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = stringResource(unit.weightLabelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            change?.let { (text, _) ->
                Text(text, style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
        if (trend.size >= 2) {
            Sparkline(points = trend, containerColor = colors.surfaceContainerLow, modifier = Modifier.weight(1f).height(Spacing.xxl))
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(IconSize.action))
    }
}

/** Before the first weigh-in: what it's for, and a tap to log one. */
@Composable
private fun BodyWeightPrompt(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.body_weight_log_click_label), onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(stringResource(R.string.body_weight_empty_title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Text(stringResource(R.string.body_weight_empty_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** An exercise with finished work: how often, and how recently. It opens its charts. */
@Composable
private fun TrainedExerciseRow(trained: TrainedExercise, state: ProgressUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val date = trained.lastPerformedAt.atZone(state.zone).toLocalDate()
    val meta = stringResource(
        R.string.progress_exercise_meta,
        pluralStringResource(R.plurals.workouts_count, trained.workouts, trained.workouts),
        relativeDayText(date, state.today) ?: workoutDateText(date, state.today),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.exercise_detail_progress_click_label), onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Column(Modifier.weight(1f)) {
            Text(trained.exercise.name, style = MaterialTheme.typography.rowTitle, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(IconSize.action))
    }
}

@Composable
private fun ProgressTabSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.progress_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(WeekSkeletonHeight), MaterialTheme.shapes.large)
            Spacer(Modifier.height(Spacing.lg))
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl + Spacing.xl), MaterialTheme.shapes.large)
        }
    }
}

private val WeekSkeletonHeight = Spacing.xxl * 5
private const val CONTENT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun ProgressScreenPreview() {
    LiftBookTheme {
        ProgressScreen(
            state = ProgressPreviewData.progress(),
            logWeight = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ProgressScreenEmptyPreview() {
    LiftBookTheme {
        ProgressScreen(
            state = ProgressUiState(isLoading = false, today = ProgressPreviewData.today, weightUnit = WeightUnit.KG),
            logWeight = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
