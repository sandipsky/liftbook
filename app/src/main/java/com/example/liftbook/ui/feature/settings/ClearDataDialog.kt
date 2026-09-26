package com.example.liftbook.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.liftbook.R
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Asks before deleting everything (FR-6.5), by having the user type a word: a tap can be an
 * accident, typing "delete" can't. It says when the last backup was, since that's the only way
 * back, and the button stays off until the word matches.
 */
@Composable
fun ClearDataDialog(
    confirmation: TextFieldState,
    lastExportedAt: Instant?,
    today: LocalDate,
    zone: ZoneId,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val word = stringResource(R.string.data_clear_confirm_word)
    val locale = LocalConfiguration.current.locales[0]
    val matches = confirmation.text.toString().trim().lowercase(locale) == word.lowercase(locale)
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_clear_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.data_clear_dialog_body))
                Text(
                    text = if (lastExportedAt != null) {
                        stringResource(R.string.data_clear_dialog_last_backup, backupDayText(lastExportedAt, today, zone))
                    } else {
                        stringResource(R.string.data_clear_dialog_no_backup)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                OutlinedTextField(
                    state = confirmation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.data_clear_confirm_label, word)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { if (matches) onConfirm() },
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.onSurface,
                        unfocusedBorderColor = colors.outline,
                        focusedLabelColor = colors.onSurface,
                    ),
                )
                // Ready to type: the dialog exists only to take the word. Requested here, in the
                // dialog's own window, once the field is in it.
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = matches,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) {
                Text(stringResource(R.string.data_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = colors.surfaceContainerHigh,
    )
}

@ThemePreviews
@Composable
private fun ClearDataDialogPreview() {
    LiftBookPreview {
        ClearDataDialog(
            confirmation = rememberTextFieldState(),
            lastExportedAt = Instant.parse("2026-09-12T08:00:00Z"),
            today = LocalDate.of(2026, 9, 26),
            zone = ZoneOffset.UTC,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
