package com.example.liftbook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.ThemePreviews

/**
 * Shown when starting a routine while a different workout is still in progress: only one can
 * run at a time (FR-3.1), so the way forward is back to the one that's running.
 */
@Composable
fun WorkoutInProgressDialog(
    workoutName: String,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.start_other_active_title, workoutName),
        text = stringResource(R.string.start_other_active_body),
        confirmLabel = stringResource(R.string.start_other_active_resume),
        dismissLabel = stringResource(R.string.start_other_active_dismiss),
        onConfirm = onResume,
        onDismiss = onDismiss,
    )
}

@ThemePreviews
@Composable
private fun WorkoutInProgressDialogPreview() {
    LiftBookPreview {
        WorkoutInProgressDialog(workoutName = "Pull", onResume = {}, onDismiss = {})
    }
}
