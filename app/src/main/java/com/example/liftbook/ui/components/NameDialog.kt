package com.example.liftbook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.liftbook.R
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.ThemePreviews

/**
 * Asks for a name: one field, pre-filled with the current one and fully selected so typing
 * replaces it, with the keyboard up. [confirmLabel] stays disabled while the name would be
 * blank, and Done on the keyboard confirms. What's typed is handed over as is; the caller
 * normalises it.
 */
@Composable
fun NameDialog(
    title: String,
    initialName: String,
    label: String,
    confirmLabel: String,
    maxLength: Int,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTextFieldState(initialName, initialSelection = TextRange(0, initialName.length))
    val canConfirm = state.text.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(label) },
                inputTransformation = InputTransformation.maxLength(maxLength),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = { if (canConfirm) onConfirm(state.text.toString()) },
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.onSurface,
                    unfocusedBorderColor = colors.outline,
                    focusedLabelColor = colors.onSurface,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.text.toString()) }, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = colors.surfaceContainerHigh,
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@ThemePreviews
@Composable
private fun NameDialogPreview() {
    LiftBookPreview {
        NameDialog(
            title = "Rename workout",
            initialName = "Morning workout",
            label = "Name",
            confirmLabel = "Rename",
            maxLength = 60,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
