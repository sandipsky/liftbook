package com.example.liftbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/**
 * A free-text note (FR-3.9): a quiet tonal field with a note icon, growing to a few lines as
 * it's written. It reads as part of the page until it's used — no box, no label, just the
 * placeholder saying what it's for. [label] names it for TalkBack.
 */
@Composable
fun NoteField(
    state: TextFieldState,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    autoFocus: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val shape = MaterialTheme.shapes.medium
    BasicTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = label },
        inputTransformation = InputTransformation.maxLength(MAX_NOTE_LENGTH),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = MAX_VISIBLE_LINES),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.onSurface),
        decorator = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinHeight)
                    .clip(shape)
                    .background(containerColor)
                    .border(OutlineWidth, if (focused) colors.onSurface else Color.Transparent, shape)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.inline),
                )
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    innerTextField()
                }
            }
        },
    )
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

private const val MAX_NOTE_LENGTH = 500
private const val MAX_VISIBLE_LINES = 6
private val MinHeight = 48.dp
private val OutlineWidth = 2.dp

@ThemePreviews
@Composable
private fun NoteFieldPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            NoteField(rememberTextFieldState(), placeholder = "Add a note about this workout", label = "Workout note")
            NoteField(rememberTextFieldState("Left shoulder a bit tight on the last set."), placeholder = "Add a note", label = "Note")
        }
    }
}
