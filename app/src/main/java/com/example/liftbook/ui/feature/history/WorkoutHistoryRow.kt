package com.example.liftbook.ui.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.dayOfMonthText
import com.example.liftbook.ui.components.displayWeight
import com.example.liftbook.ui.components.spokenDateText
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.components.weekdayText
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.components.workoutDurationSpoken
import com.example.liftbook.ui.components.workoutDurationText
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.time.LocalDate
import java.time.ZoneId

/**
 * A finished workout in the history (FR-4.1). The day sits on the leading edge, where the eye
 * runs down the list; what the workout was fills the middle; and the trailing edge holds the two
 * numbers it came to — the volume, or the sets done when nothing was lifted, and how long it took.
 * Among workouts of one day, [showDay] off trades the day for the time each started.
 */
@Composable
fun WorkoutHistoryRow(
    workout: WorkoutListItem,
    today: LocalDate,
    zone: ZoneId,
    weightUnit: WeightUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDay: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val colors = MaterialTheme.colorScheme
    val separator = stringResource(R.string.list_separator)
    val startTime = if (showDay) null else timeOfDayText(workout.startedAt.atZone(zone).toLocalTime())
    val exercises = remember(workout.exerciseNames, separator, startTime) {
        (listOfNotNull(startTime) + workout.exerciseNames).joinToString(separator)
    }
    val durationSpoken = workoutDurationSpoken(workout.durationSeconds)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .clickable(onClickLabel = stringResource(R.string.history_workout_click_label), onClick = onClick)
            .padding(start = if (showDay) Spacing.xs else Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (showDay) DayBadge(date = workout.startedAt.atZone(zone).toLocalDate(), today = today)
        Column(Modifier.weight(1f)) {
            Text(
                text = workout.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (exercises.isNotEmpty()) {
                Text(
                    text = exercises,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            WorkoutAmount(workout, weightUnit)
            Text(
                text = workoutDurationText(workout.durationSeconds),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = durationSpoken },
            )
        }
    }
}

/** The day, as a calendar leaf: the weekday receding above its number. TalkBack hears the date in full. */
@Composable
private fun DayBadge(date: LocalDate, today: LocalDate) {
    val spoken = spokenDateText(date, today)
    Column(
        Modifier
            .width(DayBadgeWidth)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = weekdayText(date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = dayOfMonthText(date),
            style = MaterialTheme.typography.titleLarge.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** The volume with its unit receding — or, for a workout with nothing lifted, the sets done. */
@Composable
private fun WorkoutAmount(workout: WorkoutListItem, weightUnit: WeightUnit) {
    val (value, unit) = if (workout.volumeKg > 0.0) {
        displayWeight(workout.volumeKg, weightUnit, maxFractionDigits = 0) to stringResource(weightUnit.weightLabelRes())
    } else {
        workout.completedSets.toString() to pluralStringResource(R.plurals.sets_unit, workout.completedSets)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** A row's placeholder while the history loads. */
@Composable
fun WorkoutHistoryRowSkeleton(modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.fillMaxWidth().height(RowSkeletonHeight), MaterialTheme.shapes.large)
}

private val DayBadgeWidth = 48.dp
private val RowSkeletonHeight = Spacing.xxl + Spacing.lg

@ThemePreviews
@Composable
private fun WorkoutHistoryRowPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            HistoryPreviewData.workouts.take(3).forEach { workout ->
                WorkoutHistoryRow(
                    workout = workout,
                    today = HistoryPreviewData.today,
                    zone = HistoryPreviewData.zone,
                    weightUnit = WeightUnit.KG,
                    onClick = {},
                )
            }
        }
    }
}
