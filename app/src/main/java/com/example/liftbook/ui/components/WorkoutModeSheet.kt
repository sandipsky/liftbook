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
import androidx.compose.material.icons.outlined.Checklist
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
 * Asked wherever a workout starts (FR-3.1, FR-3.11): log it as it happens, or record one
 * already done. Two choices, each saying what it means, as rows at the foot of the screen where
 * a thumb reaches them; one tap picks. [title] names what's starting — the routine, or a new
 * workout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutModeSheet(
    title: String,
    onPick: (WorkoutMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        WorkoutModeChoices(title = title, onPick = onPick)
    }
}

@Composable
private fun WorkoutModeChoices(title: String, onPick: (WorkoutMode) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs).semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.md))
        ModeChoice(
            icon = Icons.Outlined.PlayArrow,
            title = stringResource(R.string.mode_start_title),
            body = stringResource(R.string.mode_start_body),
            onClick = { onPick(WorkoutMode.LIVE) },
        )
        ModeChoice(
            icon = Icons.Outlined.Checklist,
            title = stringResource(R.string.mode_record_title),
            body = stringResource(R.string.mode_record_body),
            onClick = { onPick(WorkoutMode.RECORD) },
        )
    }
}

/** One way to log the workout: what it's called, what it means, and its icon in the accent's soft tone. */
@Composable
private fun ModeChoice(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .heightIn(min = ChoiceHeight)
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(IconDiscSize)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(IconSize.action))
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Spacer(Modifier.height(Spacing.xxs))
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

private val ChoiceHeight = 64.dp
private val IconDiscSize = 40.dp

@ThemePreviews
@Composable
private fun WorkoutModeChoicesPreview() {
    LiftBookPreview {
        WorkoutModeChoices(title = "Push", onPick = {}, modifier = Modifier.padding(top = Spacing.md))
    }
}
