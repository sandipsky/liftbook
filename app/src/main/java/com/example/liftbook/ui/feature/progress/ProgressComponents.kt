package com.example.liftbook.ui.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.ui.components.MetricValueText
import com.example.liftbook.ui.components.ProgressRangeOrder
import com.example.liftbook.ui.components.SegmentedSelector
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.labelRes
import com.example.liftbook.ui.components.spokenRes
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers

/*
 * The parts the progress screens share (FR-5.1, FR-5.4): the number a chart leads with, its
 * range, and the list of what it's drawn from.
 */

/** Which way a series went. The arrow always comes with the signed number, never alone. */
enum class Direction { Up, Down, Flat }

fun directionOf(change: Double, isVisible: Boolean): Direction = when {
    !isVisible -> Direction.Flat
    change > 0 -> Direction.Up
    change < 0 -> Direction.Down
    else -> Direction.Flat
}

/**
 * The number a chart leads with — the latest, or the point being scrubbed — as the strongest
 * thing on the screen, with a quiet line naming it above and how it's moved below. The change
 * line keeps its space while scrubbing, so the chart beneath doesn't jump.
 */
@Composable
fun ChartHeadline(
    caption: String,
    value: MetricValueText,
    change: MetricValueText?,
    direction: Direction,
    modifier: Modifier = Modifier,
    showChange: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = value.spoken },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = value.number,
                style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                color = colors.onSurface,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            value.unit?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, modifier = Modifier.alignByBaseline())
            }
        }
        if (change != null) {
            Row(
                modifier = Modifier
                    .padding(top = Spacing.xxs)
                    .alpha(if (showChange) 1f else 0f)
                    .clearAndSetSemantics { if (showChange) contentDescription = change.spoken },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Icon(
                    imageVector = when (direction) {
                        Direction.Up -> Icons.AutoMirrored.Outlined.TrendingUp
                        Direction.Down -> Icons.AutoMirrored.Outlined.TrendingDown
                        Direction.Flat -> Icons.AutoMirrored.Outlined.TrendingFlat
                    },
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.inline),
                )
                Text(
                    text = change.number,
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** How far back a chart looks: 1M · 3M · 6M · 1Y · All, below the chart where the thumb is. */
@Composable
fun RangeSelector(range: ProgressRange, onSelect: (ProgressRange) -> Unit, modifier: Modifier = Modifier) {
    SegmentedSelector(
        options = ProgressRangeOrder,
        selected = range,
        onSelect = onSelect,
        label = { stringResource(it.labelRes()) },
        spokenLabel = { stringResource(it.spokenRes()) },
        modifier = modifier,
    )
}

/**
 * One value a chart is drawn from: its day and what it came from on the leading edge, the value
 * on the trailing edge where a column of them reads straight down. Tapping it opens the source.
 */
@Composable
fun ProgressRow(
    title: String,
    subtitle: String?,
    value: MetricValueText,
    onClick: () -> Unit,
    clickLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = value.spoken },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = value.number,
                style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                color = colors.onSurface,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            value.unit?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

/**
 * What stands where a chart would, when there's nothing in its range to draw: what's missing,
 * in a line, and — when there's more outside the range — the way to see it. Chart-sized, so
 * picking a range never makes the page jump.
 */
@Composable
fun ChartEmptyPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = ChartPanelHeight)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Spacer(Modifier.height(Spacing.xxs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (actionLabel != null) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A chart screen's placeholder while it loads: the headline, the chart and the range. */
@Composable
fun ChartSkeleton(modifier: Modifier = Modifier) {
    Column(modifier) {
        SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(Spacing.sm))
        Spacer(Modifier.height(Spacing.xs))
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(Spacing.xl))
        Spacer(Modifier.height(Spacing.lg))
        SkeletonBlock(Modifier.fillMaxWidth().height(ChartPanelHeight), MaterialTheme.shapes.large)
        Spacer(Modifier.height(Spacing.md))
        SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl), MaterialTheme.shapes.large)
    }
}

/** The chart's own height, so a panel standing in for it fills the same space. */
internal val ChartPanelHeight = 200.dp
private val RowMinHeight = 56.dp

@ThemePreviews
@Composable
private fun ChartHeadlinePreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ChartHeadline(
                caption = "Latest · Thu, 25 Sep",
                value = MetricValueText("116.7", "kg", "116.7 kg"),
                change = MetricValueText("+8.3 kg since 26 Jun", null, "Up 8.3 kg since 26 June"),
                direction = Direction.Up,
            )
            ProgressRow(
                title = "Thu, 25 Sep",
                subtitle = "Push",
                value = MetricValueText("116.7", "kg", "116.7 kg"),
                onClick = {},
                clickLabel = "view workout",
            )
            ChartEmptyPanel(
                title = "Nothing in the last month",
                body = "Your earlier workouts are still charted.",
                actionLabel = "Show all time",
            )
        }
    }
}
