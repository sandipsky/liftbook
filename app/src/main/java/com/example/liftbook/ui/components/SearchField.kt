package com.example.liftbook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.example.liftbook.R
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/** A pill-shaped search input: a tonal fill instead of an outline, and a clear button once there's text. */
@Composable
fun SearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    TextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(IconSize.inline))
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = state.text.isNotEmpty(),
                enter = fadeIn(tween(QUICK_FADE_MILLIS)),
                exit = fadeOut(tween(QUICK_FADE_MILLIS)),
            ) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_clear_search),
                        modifier = Modifier.size(IconSize.inline),
                    )
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        onKeyboardAction = { keyboard?.hide() },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = colors.onSurface,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
            focusedTrailingIconColor = colors.onSurfaceVariant,
            unfocusedTrailingIconColor = colors.onSurfaceVariant,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant,
        ),
    )
}

private const val QUICK_FADE_MILLIS = 150

@ThemePreviews
@Composable
private fun SearchFieldPreview() {
    LiftBookPreview {
        SearchField(
            state = rememberTextFieldState(),
            placeholder = "Search 129 exercises",
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}

@ThemePreviews
@Composable
private fun SearchFieldFilledPreview() {
    LiftBookPreview {
        SearchField(
            state = rememberTextFieldState("bench"),
            placeholder = "Search 129 exercises",
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
