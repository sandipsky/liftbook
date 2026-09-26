package com.example.liftbook.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/**
 * One choice from a handful of short options — a chart's time range — as a tonal track of equal
 * segments with the chosen one in ink, the way chips show selection. The ink slides to a new
 * choice rather than jumping. Lighter than a row of chips when a screen already has one, and
 * every segment is a full 48dp target. TalkBack hears each as a radio button, by [spokenLabel].
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    spokenLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = TrackInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * index,
            animationSpec = tween(SLIDE_MILLIS, easing = FastOutSlowInEasing),
            label = "segmentIndicator",
        )
        Box(
            Modifier
                // Read in the placement block, so the slide re-places the ink without recomposing.
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .height(SegmentHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.inverseSurface),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                val content by animateColorAsState(
                    targetValue = if (isSelected) colors.inverseOnSurface else colors.onSurface,
                    animationSpec = tween(SLIDE_MILLIS),
                    label = "segmentLabel",
                )
                val spoken = spokenLabel(option)
                Box(
                    Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .height(SegmentHeight)
                        .clip(MaterialTheme.shapes.medium)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { if (!isSelected) onSelect(option) })
                        .semantics { contentDescription = spoken },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                }
            }
        }
    }
}

/** The track's inset around the ink: concentric corners, large = medium + this. */
private val TrackInset = Spacing.xxs
private val SegmentHeight = 40.dp
private const val SLIDE_MILLIS = 200

@ThemePreviews
@Composable
private fun SegmentedSelectorPreview() {
    LiftBookPreview {
        SegmentedSelector(
            options = listOf("1M", "3M", "6M", "1Y", "All"),
            selected = "3M",
            onSelect = {},
            label = { it },
            spokenLabel = { it },
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
