package com.example.liftbook.ui.feature.summary

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.example.liftbook.ui.components.BottomActionBar
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
fun WorkoutSummaryRoute(
    workoutId: String,
    onDone: () -> Unit,
) {
    val viewModel = hiltViewModel<WorkoutSummaryViewModel, WorkoutSummaryViewModel.Factory>(
        creationCallback = { factory -> factory.create(workoutId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutSummaryScreen(state = state, onAction = { action -> if (action == WorkoutSummaryAction.Done) onDone() })
}

/**
 * The moment after finishing (FR-3.8): the three numbers that sum the workout up, any personal
 * records — in the accent, the one place on the screen it's used — and what was done, set by set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    state: WorkoutSummaryUiState,
    onAction: (WorkoutSummaryAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headerScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val workout = state.workout

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = workout?.name.orEmpty(),
                navigation = TopBarNavigation.Close,
                onNavigationClick = { onAction(WorkoutSummaryAction.Done) },
                showTitle = headerScrolledAway,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (workout != null) {
                BottomActionBar(text = stringResource(R.string.summary_done), onClick = { onAction(WorkoutSummaryAction.Done) })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val content = when {
            state.isLoading -> SummaryContent.Loading
            workout == null || state.summary == null -> SummaryContent.None
            else -> SummaryContent.Summary
        }
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "summaryContent",
        ) { target ->
            when (target) {
                SummaryContent.Loading -> SummarySkeleton(Modifier.padding(padding))
                SummaryContent.None -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.summary_not_found_title),
                    body = stringResource(R.string.summary_not_found_body),
                    modifier = Modifier.padding(padding),
                    action = {
                        OutlinedButton(onClick = { onAction(WorkoutSummaryAction.Done) }) {
                            Text(stringResource(R.string.summary_not_found_action))
                        }
                    },
                )
                SummaryContent.Summary -> if (workout != null && state.summary != null) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + Spacing.lg,
                        ),
                    ) {
                        item(key = "header", contentType = "header") { SummaryHeader(workout, state) }
                        if (state.summary.records.isNotEmpty()) {
                            item(key = "recordsHeader", contentType = "sectionHeader") {
                                SectionHeader(
                                    title = stringResource(R.string.summary_records),
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
                                title = stringResource(R.string.summary_exercises),
                                trailing = state.exercises.size.toString(),
                                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
                            )
                        }
                        items(state.exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
                            LoggedExerciseCard(
                                exercise = exercise,
                                weightUnit = state.weightUnit,
                                modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xs),
                            )
                        }
                        workout.note?.let { note ->
                            item(key = "note", contentType = "note") {
                                Column {
                                    SectionHeader(
                                        title = stringResource(R.string.summary_note),
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
}

private enum class SummaryContent { Loading, None, Summary }

@Composable
private fun SummaryHeader(workout: Workout, state: WorkoutSummaryUiState) {
    val summary = state.summary ?: return
    val start = workout.startedAt.atZone(state.zone)
    val end = (workout.finishedAt ?: workout.startedAt).atZone(state.zone)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs),
    ) {
        // The accent marks the moment; the label says what it is, so colour isn't carrying it.
        Text(
            text = stringResource(R.string.summary_complete),
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
private fun SummarySkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(
        contentDescription = stringResource(R.string.summary_loading),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs)) {
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.35f).height(Spacing.sm))
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

private fun previewState(): WorkoutSummaryUiState {
    val finished = WorkoutPreviewData.workout.let { workout ->
        workout.copy(
            finishedAt = workout.startedAt.plusSeconds(62 * 60 + 15),
            rest = null,
            note = "Felt strong. Bench moved well.",
            exercises = workout.exercises.map { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(isCompleted = true) }) },
        )
    }
    val previous = mapOf(
        "bench" to listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(77.5, 8)), LoggedSet(SetType.NORMAL, SetMetrics.Strength(70.0, 8))),
        "ohp" to listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(47.5, 6))),
    )
    return WorkoutSummaryUiState(
        isLoading = false,
        workout = finished,
        summary = finished.summarize(previous),
        exercises = finished.recapExercises(finished.summarize(previous).recordSetIds),
        today = LocalDate.of(2026, 9, 25),
        zone = WorkoutPreviewData.zone,
    )
}

@ThemePreviews
@Composable
private fun WorkoutSummaryPreview() {
    LiftBookTheme {
        WorkoutSummaryScreen(state = previewState(), onAction = {})
    }
}

@ThemePreviews
@Composable
private fun WorkoutSummaryNoRecordsPreview() {
    val state = previewState()
    LiftBookTheme {
        WorkoutSummaryScreen(state = state.copy(summary = state.summary?.copy(records = emptyList())), onAction = {})
    }
}

@ThemePreviews
@Composable
private fun WorkoutSummaryLoadingPreview() {
    LiftBookTheme {
        WorkoutSummaryScreen(state = WorkoutSummaryUiState(), onAction = {})
    }
}
