package com.example.liftbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.time.Instant

/**
 * The workout in progress, docked above the bottom bar on every top-level screen: its name and
 * how long it's been going (FR-3.6) — or, while resting, how long the rest has left — one tap
 * from resuming. It takes the accent's soft container tone, the same as a routine's start
 * button, because a workout under way is a progress moment. This is what keeps an unfinished
 * workout from ever being lost from view (FR-3.1).
 *
 * [now] is read here rather than above, so the once-a-second tick redraws only the banner.
 */
@Composable
fun ActiveWorkoutBanner(
    name: String,
    startedAt: Instant,
    rest: RestTimer?,
    now: () -> Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val at = now()
    val resting = rest != null && !rest.isOver(at)
    val status = if (resting) {
        stringResource(R.string.banner_resting, formatDuration(restSecondsLeft(rest, at)))
    } else {
        stringResource(R.string.banner_elapsed, elapsedText(startedAt, at))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.primaryContainer)
            .clickable(onClickLabel = stringResource(R.string.banner_click_label), onClick = onClick)
            .heightIn(min = BannerHeight)
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            if (resting) Icons.Outlined.Timer else Icons.Outlined.FitnessCenter,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(IconSize.inline),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = colors.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(IconSize.action),
        )
    }
}

private val BannerHeight = 56.dp

@ThemePreviews
@Composable
private fun ActiveWorkoutBannerPreview() {
    val now = Instant.parse("2026-09-25T19:02:14Z")
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ActiveWorkoutBanner(
                name = "Push",
                startedAt = Instant.parse("2026-09-25T18:30:00Z"),
                rest = null,
                now = { now },
                onClick = {},
            )
            ActiveWorkoutBanner(
                name = "Push",
                startedAt = Instant.parse("2026-09-25T18:30:00Z"),
                rest = RestTimer(now.minusSeconds(10), now.plusSeconds(80)),
                now = { now },
                onClick = {},
            )
        }
    }
}
