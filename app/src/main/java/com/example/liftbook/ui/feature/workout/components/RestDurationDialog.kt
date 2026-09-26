package com.example.liftbook.ui.feature.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.RestTimes
import com.example.liftbook.ui.components.restDurationSpoken
import com.example.liftbook.ui.components.restDurationText
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers

/**
 * Picks a rest duration (FR-3.5): for one exercise — every time it's done — or for the default.
 * One tap picks and closes. For an exercise the first choice returns it to the default, which
 * the option names, so it's clear what "default" means.
 *
 * [selected] is the exercise's own rest, or null when it follows the default; for the default
 * itself, it's the default.
 */
@Composable
fun RestDurationDialog(
    title: String,
    body: String,
    selected: Int?,
    onSelect: (seconds: Int?) -> Unit,
    onDismiss: () -> Unit,
    /** Offered as the first choice, for an exercise; null when picking the default itself. */
    defaultSeconds: Int? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(
                    Modifier
                        .padding(top = Spacing.sm)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    if (defaultSeconds != null) {
                        val label = stringResource(R.string.rest_option_default, restDurationText(defaultSeconds))
                        val spoken = stringResource(R.string.rest_option_default, restDurationSpoken(defaultSeconds))
                        Option(label = label, spoken = spoken, selected = selected == null, onClick = { onSelect(null) })
                    }
                    RestTimes.OPTIONS.forEach { seconds ->
                        Option(
                            label = restDurationText(seconds),
                            spoken = restDurationSpoken(seconds),
                            selected = selected == seconds,
                            onClick = { onSelect(seconds) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.rest_dialog_dismiss)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun Option(label: String, spoken: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OptionHeight)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Selection in ink, not the accent, as for chips.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge.tabularNumbers(), color = MaterialTheme.colorScheme.onSurface)
    }
}

private val OptionHeight = ControlSize

@ThemePreviews
@Composable
private fun RestDurationDialogPreview() {
    LiftBookPreview {
        RestDurationDialog(
            title = "Rest timer",
            body = "After each set of Bench Press (Barbell), in every workout.",
            selected = null,
            defaultSeconds = 90,
            onSelect = {},
            onDismiss = {},
        )
    }
}
