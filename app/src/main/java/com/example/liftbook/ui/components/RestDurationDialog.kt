package com.example.liftbook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.RestTimes
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.ThemePreviews

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
    val defaultOption = defaultSeconds?.let {
        DialogOption<Int?>(
            value = null,
            label = stringResource(R.string.rest_option_default, restDurationText(it)),
            spoken = stringResource(R.string.rest_option_default, restDurationSpoken(it)),
        )
    }
    val options = listOfNotNull(defaultOption) + RestTimes.OPTIONS.map { seconds ->
        DialogOption<Int?>(seconds, label = restDurationText(seconds), spoken = restDurationSpoken(seconds))
    }
    OptionDialog(title = title, body = body, options = options, selected = selected, onSelect = onSelect, onDismiss = onDismiss)
}

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
