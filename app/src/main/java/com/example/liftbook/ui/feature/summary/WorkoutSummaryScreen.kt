package com.example.liftbook.ui.feature.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.domain.calculator.completedSets
import com.example.liftbook.domain.calculator.summarize
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseRecords
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SetMetricsText
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.StatTile
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.displayWeight
import com.example.liftbook.ui.components.setMarker
import com.example.liftbook.ui.components.setSpokenTitle
import com.example.liftbook.ui.components.spokenDuration
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.components.weightWithUnit
import com.example.liftbook.ui.components.workingSetNumbers
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.feature.workout.WorkoutPreviewData
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
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
                                RecordsBlock(records = state.summary.records, weightUnit = state.weightUnit)
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
                            ExerciseDone(
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val duration = summary.durationSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            StatTile(
                label = stringResource(R.string.summary_stat_duration),
                value = formatDuration(duration),
                spokenValue = spokenDuration(duration),
                valueStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.summary_stat_volume),
                value = displayWeight(summary.volumeKg, state.weightUnit, maxFractionDigits = 0),
                unit = stringResource(state.weightUnit.weightLabelRes()),
                valueStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.summary_stat_sets),
                value = summary.completedSets.toString(),
                valueStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * New records (FR-5.2), on the accent's soft tone: this is the moment the accent is kept for.
 * Each says what the record is and the number that set it; the trophy and the heading say it's
 * a record, so it doesn't rest on colour.
 */
@Composable
private fun RecordsBlock(records: List<ExerciseRecords>, weightUnit: WeightUnit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.primaryContainer)
            .padding(vertical = Spacing.xs),
    ) {
        records.forEach { exerciseRecords ->
            ExerciseRecordsGroup(exerciseRecords, weightUnit)
        }
    }
}

/** One exercise's records: its name once, beside the trophy, then each record and its number. */
@Composable
private fun ExerciseRecordsGroup(exerciseRecords: ExerciseRecords, weightUnit: WeightUnit) {
    val colors = MaterialTheme.colorScheme
    val exercise = exerciseRecords.exercise
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(IconSize.action),
            )
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        exerciseRecords.records.forEach { record ->
            val (label, value) = recordText(record, exercise.type, weightUnit)
            val spoken = stringResource(R.string.record_spoken, exercise.name, label, value)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = spoken }
                    // Lined up under the name, past the trophy.
                    .padding(start = IconSize.action + Spacing.sm, top = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                    color = colors.onPrimaryContainer,
                )
            }
        }
    }
}

/** A record's name and its number: "Heaviest weight" · "100 kg", "Most reps at 80 kg" · "10 reps". */
@Composable
private fun recordText(record: PersonalRecord, type: ExerciseType, weightUnit: WeightUnit): Pair<String, String> = when (record) {
    is PersonalRecord.HeaviestWeight ->
        stringResource(R.string.record_heaviest) to weightWithUnit(record.weightKg, weightUnit)
    is PersonalRecord.BestEstimatedOneRepMax ->
        // An estimate: one decimal is all the precision it has.
        stringResource(R.string.record_one_rep_max) to weightWithUnit(record.estimatedKg, weightUnit, maxFractionDigits = 1)
    is PersonalRecord.MostReps -> {
        // Bodyweight without added weight is just "most reps"; anything else names the weight.
        val label = if (type == ExerciseType.BODYWEIGHT && record.weightKg <= 0.0) {
            stringResource(R.string.record_most_reps)
        } else {
            stringResource(R.string.record_most_reps_at, weightWithUnit(record.weightKg, weightUnit))
        }
        label to pluralStringResource(R.plurals.reps_count, record.reps, record.reps)
    }
}

/** One exercise's completed sets, as in its history. */
@Composable
private fun ExerciseDone(exercise: SummaryExercise, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    val numbers = remember(exercise.sets) { workingSetNumbers(exercise.sets) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(
            text = exercise.exercise.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = Spacing.xxs),
        )
        exercise.sets.forEachIndexed { index, set -> SetLine(set = set, number = numbers[index], weightUnit = weightUnit) }
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
        exercises = finished.exercises.map { SummaryExercise(it.id, it.exercise, it.completedSets()) },
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
