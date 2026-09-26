package com.example.liftbook.ui.feature.workout.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDuration
import com.example.liftbook.domain.calculator.RestTimes
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.ui.components.restSecondsLeft
import com.example.liftbook.ui.components.spokenDuration
import com.example.liftbook.ui.feature.workout.RestAlertIssue
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.time.Instant

/**
 * The rest between sets (FR-3.5), docked at the bottom where the thumb already is: the time
 * left in large figures, a line draining along its foot, and −30s / +30s / Skip. It's ink — the
 * inverse surface — so it stands apart from the page like the transient thing it is.
 *
 * The countdown is subtraction from the stored end time, never a count, so it's right after any
 * trip to the background (architecture §6.1). At zero it says so and offers Done.
 */
@Composable
fun RestTimerBar(
    rest: RestTimer,
    now: () -> Instant,
    alertIssue: RestAlertIssue?,
    onAdjust: (seconds: Int) -> Unit,
    onSkip: () -> Unit,
    onFixAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val at = now()
    val over = rest.isOver(at)
    val secondsLeft = restSecondsLeft(rest, at)
    val totalMillis = rest.total.toMillis().coerceAtLeast(1)
    val fraction by animateFloatAsState(
        targetValue = (rest.remaining(at).toMillis().toFloat() / totalMillis).coerceIn(0f, 1f),
        // One tick's worth, linearly, so the line moves continuously between ticks.
        animationSpec = tween(TICK_MILLIS, easing = LinearEasing),
        label = "restLeft",
    )
    val spokenLeft = stringResource(R.string.rest_remaining_spoken, spokenDuration(secondsLeft))
    val textColor = colors.inverseOnSurface
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = textColor)

    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.inverseSurface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xxs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (over) R.string.rest_over else R.string.rest_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    // Announces "Rest over" when it ends; the countdown itself isn't read out every second.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    text = formatDuration(secondsLeft),
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                    color = textColor,
                    modifier = Modifier.clearAndSetSemantics { if (!over) contentDescription = spokenLeft },
                )
            }
            if (!over) {
                val subtract = pluralStringResource(R.plurals.rest_subtract_description, RestTimes.ADJUST_SECONDS, RestTimes.ADJUST_SECONDS)
                TextButton(
                    onClick = { onAdjust(-RestTimes.ADJUST_SECONDS) },
                    colors = buttonColors,
                    modifier = Modifier.heightIn(min = ControlSize).semantics { contentDescription = subtract },
                ) {
                    Text(stringResource(R.string.rest_subtract, RestTimes.ADJUST_SECONDS), style = MaterialTheme.typography.labelLarge.tabularNumbers())
                }
            }
            val add = pluralStringResource(R.plurals.rest_add_description, RestTimes.ADJUST_SECONDS, RestTimes.ADJUST_SECONDS)
            TextButton(
                onClick = { onAdjust(RestTimes.ADJUST_SECONDS) },
                colors = buttonColors,
                modifier = Modifier.heightIn(min = ControlSize).semantics { contentDescription = add },
            ) {
                Text(stringResource(R.string.rest_add, RestTimes.ADJUST_SECONDS), style = MaterialTheme.typography.labelLarge.tabularNumbers())
            }
            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.inversePrimary),
                modifier = Modifier.heightIn(min = ControlSize),
            ) {
                Text(stringResource(if (over) R.string.rest_done else R.string.rest_skip), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (alertIssue != null) {
            AlertHint(issue = alertIssue, onFix = onFixAlerts)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(ProgressHeight)
                .background(textColor.copy(alpha = TRACK_ALPHA)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(ProgressHeight)
                    .background(colors.inversePrimary),
            )
        }
    }
}

/** Why the alert may not reach the user with the screen off, and the setting that fixes it. */
@Composable
private fun AlertHint(issue: RestAlertIssue, onFix: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.inverseOnSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.xxs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            when (issue) {
                RestAlertIssue.NotificationsOff -> Icons.Outlined.NotificationsOff
                RestAlertIssue.AlarmsOff -> Icons.Outlined.AlarmOff
            },
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(IconSize.inline),
        )
        Text(
            text = stringResource(
                when (issue) {
                    RestAlertIssue.NotificationsOff -> R.string.rest_alerts_notifications_off
                    RestAlertIssue.AlarmsOff -> R.string.rest_alerts_alarms_off
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onFix,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
            modifier = Modifier.heightIn(min = ControlSize),
        ) {
            Text(stringResource(R.string.rest_alerts_fix), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val ProgressHeight = 4.dp
private const val TRACK_ALPHA = 0.16f
private const val TICK_MILLIS = 1_000

@ThemePreviews
@Composable
private fun RestTimerBarPreview() {
    val now = Instant.parse("2026-09-25T19:00:00Z")
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            RestTimerBar(
                rest = RestTimer(now.minusSeconds(37), now.plusSeconds(53)),
                now = { now },
                alertIssue = null,
                onAdjust = {},
                onSkip = {},
                onFixAlerts = {},
            )
            RestTimerBar(
                rest = RestTimer(now.minusSeconds(80), now.plusSeconds(40)),
                now = { now },
                alertIssue = RestAlertIssue.NotificationsOff,
                onAdjust = {},
                onSkip = {},
                onFixAlerts = {},
            )
            RestTimerBar(
                rest = RestTimer(now.minusSeconds(92), now.minusSeconds(2)),
                now = { now },
                alertIssue = null,
                onAdjust = {},
                onSkip = {},
                onFixAlerts = {},
            )
        }
    }
}
