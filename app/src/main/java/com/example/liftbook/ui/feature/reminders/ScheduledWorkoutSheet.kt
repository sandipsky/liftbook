package com.example.liftbook.ui.feature.reminders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.ui.components.PrimaryActionButton
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The workout a reminder was for (FR-7.3): when it starts, what it is, and Start in one tap.
 * It rises over whatever the app was showing, so answering a reminder never costs the user
 * their place; Not now puts it away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledWorkoutSheet(
    prompt: ScheduledWorkoutPrompt,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val starting by rememberUpdatedState(prompt.isStarting)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // A start under way finishes with the sheet up.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !starting }),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.md)) {
            val start = prompt.startsAt.atZone(prompt.zone)
            Text(
                stringResource(R.string.scheduled_when, nearDayText(start.toLocalDate(), prompt.today), timeOfDayText(start.toLocalTime())),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                prompt.routineName ?: stringResource(R.string.scheduled_title_plain),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (prompt.routineName != null && prompt.exerciseNames.isNotEmpty()) {
                    prompt.exerciseNames.joinToString(stringResource(R.string.list_separator))
                } else {
                    stringResource(R.string.scheduled_body_plain)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (prompt.startFailed) {
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.start_failed), style = MaterialTheme.typography.bodyMedium, color = colors.error)
            }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryActionButton(text = stringResource(R.string.scheduled_start), onClick = onStart, enabled = !prompt.isStarting)
            TextButton(
                onClick = onDismiss,
                enabled = !prompt.isStarting,
                modifier = Modifier.fillMaxWidth().heightIn(min = SecondaryActionHeight).padding(top = Spacing.xxs),
            ) {
                Text(stringResource(R.string.scheduled_not_now))
            }
        }
    }
}

private val SecondaryActionHeight = 48.dp

@ThemePreviews
@Composable
private fun ScheduledWorkoutSheetPreview() {
    LiftBookPreview {
        ScheduledWorkoutSheet(
            prompt = ScheduledWorkoutPrompt(
                startsAt = LocalDate.of(2026, 9, 26).atTime(18, 0).toInstant(ZoneOffset.UTC),
                routineName = "Push",
                exerciseNames = listOf("Bench Press (Barbell)", "Overhead Press (Barbell)", "Dips"),
                today = LocalDate.of(2026, 9, 26),
                zone = ZoneOffset.UTC,
            ),
            onStart = {},
            onDismiss = {},
        )
    }
}
