package com.example.liftbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.domain.calculator.completedSets
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseRecords
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutSummary
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.tabularNumbers

/*
 * A finished workout, read back: the summary after Finish (FR-3.8) and a past workout in the
 * history (FR-4.2) show it the same way.
 */

/** An exercise as done in a finished workout: completed sets only. */
data class RecapExercise(
    /** The workout-exercise id; an exercise done twice appears twice. */
    val id: String,
    val exercise: Exercise,
    val sets: List<LoggedSet>,
    /** The indices of [sets] that set one of the workout's records (FR-5.2). */
    val recordSets: Set<Int> = emptySet(),
)

/**
 * The exercises with something done, in workout order: each exercise's completed sets, as
 * [completedSets] reads them, flagged where their id is in [recordSetIds].
 */
fun Workout.recapExercises(recordSetIds: Set<String> = emptySet()): List<RecapExercise> = exercises.mapNotNull { exercise ->
    val done = exercise.sets.filter { it.isCompleted }.mapNotNull { set ->
        set.values.metricsFor(exercise.exercise.type)?.let { set.id to LoggedSet(set.setType, it) }
    }
    if (done.isEmpty()) return@mapNotNull null
    RecapExercise(
        id = exercise.id,
        exercise = exercise.exercise,
        sets = done.map { it.second },
        recordSets = done.indices.filterTo(HashSet()) { done[it].first in recordSetIds },
    )
}

/** The three numbers that sum a workout up: how long, how much, how many sets. */
@Composable
fun WorkoutStatsRow(summary: WorkoutSummary, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
            value = displayWeight(summary.volumeKg, weightUnit, maxFractionDigits = 0),
            unit = stringResource(weightUnit.weightLabelRes()),
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

/**
 * Records set in the workout (FR-5.2), on the accent's soft tone: this is the moment the accent
 * is kept for. Each says what the record is and the number that set it; the trophy and the
 * heading above say it's a record, so it doesn't rest on colour.
 */
@Composable
fun PersonalRecordsBlock(records: List<ExerciseRecords>, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(vertical = Spacing.xs),
    ) {
        records.forEach { exerciseRecords -> ExerciseRecordsGroup(exerciseRecords, weightUnit) }
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

/**
 * One exercise's completed sets, as in its history. With [onClick] the card opens the exercise,
 * and TalkBack says so.
 */
@Composable
fun LoggedExerciseCard(
    exercise: RecapExercise,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val numbers = remember(exercise.sets) { workingSetNumbers(exercise.sets) }
    val clickLabel = stringResource(R.string.routine_exercise_click_label)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = clickLabel, onClick = onClick) else Modifier)
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
        exercise.sets.forEachIndexed { index, set ->
            LoggedSetLine(set = set, number = numbers[index], weightUnit = weightUnit, isRecord = index in exercise.recordSets)
        }
    }
}

/**
 * A completed set on one line: its marker — number, or W / D / F — then what it recorded. A set
 * that set a record (FR-5.2) carries the trophy on its trailing edge, which TalkBack reads out.
 */
@Composable
fun LoggedSetLine(set: LoggedSet, number: Int?, weightUnit: WeightUnit, modifier: Modifier = Modifier, isRecord: Boolean = false) {
    val spokenTitle = setSpokenTitle(set.setType, number)
    Row(
        modifier = modifier
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
        SetMetricsText(metrics = set.metrics, weightUnit = weightUnit, modifier = Modifier.weight(1f))
        if (isRecord) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = stringResource(R.string.record_flag),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.inline),
            )
        }
    }
}
