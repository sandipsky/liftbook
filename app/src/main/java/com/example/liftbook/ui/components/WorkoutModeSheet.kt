package com.example.liftbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.WorkoutMode
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/**
 * Asks how a workout is to be logged before it begins (FR-3.1, FR-3.11): as it happens, with the
 * clock and the rest timer, or after the fact, typed in with neither. It rises from the bottom,
 * where the thumb already is after tapping Start, and one tap picks and closes. [title] names
 * what's about to start: the routine, or "New workout".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutModeSheet(
    title: String,
    onPick: (WorkoutMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.lg))
            // Starting as it happens is the usual way, so it carries the accent's soft tone.
            ModeOption(
                icon = Icons.Outlined.PlayArrow,
                title = stringResource(R.string.workout_mode_live_title),
                body = stringResource(R.string.workout_mode_live_body),
                iconContainer = colors.primaryContainer,
                iconTint = colors.onPrimaryContainer,
                onClick = { onPick(WorkoutMode.LIVE) },
            )
            Spacer(Modifier.height(Spacing.xs))
            ModeOption(
                icon = Icons.Outlined.EditNote,
                title = stringResource(R.string.workout_mode_recorded_title),
                body = stringResource(R.string.workout_mode_recorded_body),
                iconContainer = colors.surfaceContainerHighest,
                iconTint = colors.onSurface,
                onClick = { onPick(WorkoutMode.RECORDED) },
            )
        }
    }
}

@Composable
private fun ModeOption(
    icon: ImageVector,
    title: String,
    body: String,
    iconContainer: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OptionHeight)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier.size(IconDiscSize).clip(CircleShape).background(iconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(IconSize.action))
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(Spacing.xxs))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

private val OptionHeight = 72.dp
private val IconDiscSize = 40.dp

@ThemePreviews
@Composable
private fun WorkoutModeSheetPreview() {
    LiftBookPreview {
        WorkoutModeSheet(title = "Push", onPick = {}, onDismiss = {})
    }
}
