package com.example.liftbook.ui.feature.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.WeekSummary
import com.example.liftbook.domain.model.WeeklySummary
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.displayWeight
import com.example.liftbook.ui.components.labelRes
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers

/**
 * This week beside last (FR-5.3), on one tonal surface: the three totals as a small table — this
 * week's numbers strongest, last week's receding beside them — then the working sets each muscle
 * got, as bars. A bar is this week; the tick across it is where last week reached.
 */
@Composable
fun WeeklySummaryBlock(week: WeeklySummary, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(Spacing.md),
    ) {
        ComparisonTable(week, weightUnit)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = stringResource(R.string.week_sets_per_muscle),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val muscles = remember(week) { musclesInOrder(week) }
        if (muscles.isEmpty()) {
            Text(
                text = stringResource(R.string.week_sets_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        } else {
            MuscleLegend(Modifier.padding(top = Spacing.xxs, bottom = Spacing.xs))
            val most = muscles.maxOf { maxOf(week.current.setsByMuscle[it] ?: 0, week.previous.setsByMuscle[it] ?: 0) }
            muscles.forEach { muscle ->
                MuscleBar(
                    muscle = muscle,
                    current = week.current.setsByMuscle[muscle] ?: 0,
                    previous = week.previous.setsByMuscle[muscle] ?: 0,
                    most = most,
                    modifier = Modifier.padding(vertical = Spacing.xxs),
                )
            }
        }
    }
}

/** Muscles either week trained, this week's most-trained first. */
private fun musclesInOrder(week: WeeklySummary): List<MuscleGroup> =
    (week.current.setsByMuscle.keys + week.previous.setsByMuscle.keys)
        .sortedWith(
            compareByDescending<MuscleGroup> { week.current.setsByMuscle[it] ?: 0 }
                .thenByDescending { week.previous.setsByMuscle[it] ?: 0 }
                .thenBy { it.ordinal },
        )

/**
 * The week's totals against last week's. The two columns are told apart by their headings and
 * by weight and size — this week strongest — never by a second colour.
 */
@Composable
private fun ComparisonTable(week: WeeklySummary, weightUnit: WeightUnit) {
    val thisWeek = stringResource(R.string.week_this_week)
    val lastWeek = stringResource(R.string.week_last_week)
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Spacer(Modifier.weight(LABEL_WEIGHT))
        ColumnHeading(thisWeek)
        ColumnHeading(lastWeek)
    }
    val unit = stringResource(weightUnit.weightLabelRes())
    ComparisonRow(
        label = stringResource(R.string.week_workouts),
        current = week.current.workouts.toString(),
        previous = week.previous.workouts.toString(),
        thisWeek = thisWeek,
        lastWeek = lastWeek,
    )
    ComparisonRow(
        label = stringResource(R.string.week_volume),
        current = displayWeight(week.current.volumeKg, weightUnit, maxFractionDigits = 0),
        previous = displayWeight(week.previous.volumeKg, weightUnit, maxFractionDigits = 0),
        unit = unit,
        thisWeek = thisWeek,
        lastWeek = lastWeek,
    )
    ComparisonRow(
        label = stringResource(R.string.week_sets),
        current = week.current.sets.toString(),
        previous = week.previous.sets.toString(),
        thisWeek = thisWeek,
        lastWeek = lastWeek,
    )
}

@Composable
private fun RowScope.ColumnHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
    )
}

/** One total: its name receding on the left, this week and last on the right. Read as one line by TalkBack. */
@Composable
private fun ComparisonRow(
    label: String,
    current: String,
    previous: String,
    thisWeek: String,
    lastWeek: String,
    unit: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val withUnit = { value: String -> listOfNotNull(value, unit).joinToString(" ") }
    val spoken = stringResource(R.string.week_row_spoken, label, thisWeek, withUnit(current), lastWeek, withUnit(previous))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        ComparisonValue(current, unit, MaterialTheme.typography.titleLarge, emphasised = true)
        ComparisonValue(previous, unit, MaterialTheme.typography.titleMedium, emphasised = false)
    }
}

@Composable
private fun RowScope.ComparisonValue(value: String, unit: String?, style: TextStyle, emphasised: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.End)) {
        Text(
            text = value,
            style = style.tabularNumbers(),
            color = if (emphasised) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (unit != null) {
            Text(unit, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

/** What the bar and the tick are, by shape. */
@Composable
private fun MuscleLegend(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.size(width = Spacing.sm, height = BarHeight).clip(CircleShape).background(colors.onSurface))
        Text(stringResource(R.string.week_this_week), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.xs))
        Box(Modifier.size(width = TickWidth, height = TickHeight).background(colors.onSurfaceVariant))
        Text(stringResource(R.string.week_last_week), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/**
 * A muscle's working sets: its name and this week's count above, the bar below — stacked, so a
 * long name or a large font never squeezes the bar. The bar grows to its new length when the
 * week changes, rather than jumping.
 */
@Composable
private fun MuscleBar(muscle: MuscleGroup, current: Int, previous: Int, most: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(muscle.labelRes())
    val spoken = stringResource(
        R.string.week_muscle_spoken,
        name,
        pluralStringResource(R.plurals.sets_count, current, current),
        pluralStringResource(R.plurals.sets_count, previous, previous),
    )
    val fraction by animateFloatAsState(
        targetValue = if (most > 0) current.toFloat() / most else 0f,
        animationSpec = tween(BAR_MILLIS),
        label = "muscleBar",
    )
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = current.toString(),
                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                color = colors.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        BoxWithConstraints(Modifier.fillMaxWidth().height(TickHeight), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(BarHeight).clip(CircleShape).background(colors.surfaceContainerHighest))
            if (current > 0) {
                Box(Modifier.fillMaxWidth(fraction).height(BarHeight).clip(CircleShape).background(colors.onSurface))
            }
            if (previous > 0 && most > 0) {
                val at = maxWidth * (previous.toFloat() / most)
                Box(
                    Modifier
                        .offset(x = (at - TickWidth).coerceAtLeast(0.dp))
                        .size(width = TickWidth, height = TickHeight)
                        .background(colors.onSurfaceVariant),
                )
            }
        }
    }
}

private const val LABEL_WEIGHT = 1.1f
private const val BAR_MILLIS = 250
private val BarHeight = 8.dp
private val TickHeight = 16.dp
private val TickWidth = 2.dp

@ThemePreviews
@Composable
private fun WeeklySummaryBlockPreview() {
    LiftBookPreview {
        WeeklySummaryBlock(week = ProgressPreviewData.week, weightUnit = WeightUnit.KG, modifier = Modifier.padding(Spacing.gutter))
    }
}

@ThemePreviews
@Composable
private fun WeeklySummaryBlockEmptyPreview() {
    val empty = WeekSummary(start = ProgressPreviewData.today, workouts = 0, volumeKg = 0.0, setsByMuscle = emptyMap())
    LiftBookPreview {
        WeeklySummaryBlock(
            week = WeeklySummary(current = empty, previous = empty.copy(start = empty.start.minusWeeks(1))),
            weightUnit = WeightUnit.KG,
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
