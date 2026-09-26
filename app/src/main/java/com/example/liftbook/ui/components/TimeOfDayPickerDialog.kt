package com.example.liftbook.ui.components

import android.text.format.DateFormat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.LocalTime

/** Picks a time of day on the clock dial, in the user's own 12- or 24-hour format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayPickerDialog(title: String, selected: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = selected.hour,
        initialMinute = selected.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        TimePicker(state = state)
    }
}

@ThemePreviews
@Composable
private fun TimeOfDayPickerDialogPreview() {
    LiftBookPreview {
        TimeOfDayPickerDialog(title = "Starts at", selected = LocalTime.of(18, 0), onPick = {}, onDismiss = {})
    }
}
