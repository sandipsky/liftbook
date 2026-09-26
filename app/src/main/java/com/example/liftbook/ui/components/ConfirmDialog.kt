package com.example.liftbook.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.ThemePreviews

/** Asks before an action that loses work. [destructive] colours the confirm action as an error. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@ThemePreviews
@Composable
private fun ConfirmDialogPreview() {
    LiftBookPreview {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your edits to this exercise won’t be saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
    }
}
