package com.example.liftbook.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.core.format.decimalSeparators
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers

/** What a [NumberField] accepts. */
enum class NumberFieldKind(internal val defaultMaxLength: Int) {
    /** Whole numbers: sets, reps. */
    Whole(defaultMaxLength = 3),

    /** A decimal, with "." or "," as its mark and at most two decimals: weight, distance. */
    Decimal(defaultMaxLength = 6),

    /** A duration typed as digits that fill from the right, microwave-style: "130" shows 1:30. */
    Duration(defaultMaxLength = 4),
}

/**
 * A compact, labelled number input: the label small above, the value large in tabular figures,
 * the unit receding at the trailing edge. A tonal fill rather than an outline, like the search
 * field. Focus draws an ink outline; an error draws an error-coloured one and tints the label —
 * the screen always pairs it with a message saying what's wrong, so colour never carries it alone.
 * The whole tile is the touch target.
 */
@Composable
fun NumberField(
    state: TextFieldState,
    label: String,
    kind: NumberFieldKind,
    modifier: Modifier = Modifier,
    unit: String? = null,
    maxLength: Int = kind.defaultMaxLength,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val separators = remember { decimalSeparators() }
    val placeholder = stringResource(if (kind == NumberFieldKind.Duration) R.string.duration_field_empty else R.string.number_field_empty)
    val outline = when {
        isError -> colors.error
        focused -> colors.onSurface
        else -> Color.Transparent
    }
    val shape = MaterialTheme.shapes.medium

    BasicTextField(
        state = state,
        modifier = modifier,
        inputTransformation = remember(kind, maxLength, separators) { NumberInputTransformation(kind, maxLength, separators) },
        outputTransformation = if (kind == NumberFieldKind.Duration) DurationOutputTransformation else null,
        textStyle = MaterialTheme.typography.titleLarge.tabularNumbers().copy(color = colors.onSurface),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (kind == NumberFieldKind.Decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        onKeyboardAction = {
            if (imeAction == ImeAction.Next) focusManager.moveFocus(FocusDirection.Next) else focusManager.clearFocus()
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.onSurface),
        decorator = { innerTextField ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surfaceContainerHigh)
                    .border(OutlineWidth, outline, shape)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isError) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Box(Modifier.weight(1f)) {
                        // Hidden while focused, where it would sit under the cursor.
                        if (state.text.isEmpty() && !focused) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    if (unit != null) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    )
}

/**
 * A [NumberField] for a table cell: no label — the column header is the label — and the value
 * centred, large, in tabular figures, so a column of sets reads straight down. Tapping it
 * selects what's there, so a pre-filled value is replaced by typing, not edited around.
 *
 * [settled] marks a value that's been logged: the tonal fill fades away and the number stands on
 * the row, still editable. [contentDescription] names the cell for TalkBack ("Set 2, weight in
 * kg"), since there's no visible label; [errorDescription] is what TalkBack says is wrong while
 * [isError], since the outline alone can't be heard.
 */
@Composable
fun NumberCell(
    state: TextFieldState,
    kind: NumberFieldKind,
    contentDescription: String,
    modifier: Modifier = Modifier,
    maxLength: Int = kind.defaultMaxLength,
    isError: Boolean = false,
    errorDescription: String? = null,
    settled: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val separators = remember { decimalSeparators() }
    val placeholder = stringResource(if (kind == NumberFieldKind.Duration) R.string.duration_field_empty else R.string.number_field_empty)
    var cellSize by remember { mutableStateOf(IntSize.Zero) }
    val fill by animateColorAsState(
        targetValue = if (settled && !focused && !isError) Color.Transparent else colors.surfaceContainerHigh,
        animationSpec = tween(SETTLE_MILLIS),
        label = "cellFill",
    )
    val outline = when {
        isError -> colors.error
        focused -> colors.onSurface
        else -> Color.Transparent
    }
    val shape = MaterialTheme.shapes.medium

    // After the tap that focused the cell has placed its cursor, so the selection sticks.
    LaunchedEffect(focused) {
        if (focused) state.edit { selection = TextRange(0, length) }
    }
    // The field scrolls into view as it's focused, but the keyboard is still rising then. Once it
    // has settled, bring the cell — and a margin below it, so its row's other controls show — into
    // view again. Only the focused cell reads the keyboard's height, so only it redraws as it moves.
    val bringIntoView = remember { BringIntoViewRequester() }
    if (focused) {
        val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
        val margin = with(LocalDensity.current) { Spacing.md.toPx() }
        LaunchedEffect(keyboardHeight) {
            bringIntoView.bringIntoView(Rect(0f, 0f, cellSize.width.toFloat(), cellSize.height + margin))
        }
    }

    BasicTextField(
        state = state,
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .onSizeChanged { cellSize = it }
            .semantics {
                this.contentDescription = contentDescription
                if (isError && errorDescription != null) error(errorDescription)
            },
        inputTransformation = remember(kind, maxLength, separators) { NumberInputTransformation(kind, maxLength, separators) },
        outputTransformation = if (kind == NumberFieldKind.Duration) DurationOutputTransformation else null,
        textStyle = MaterialTheme.typography.titleLarge.tabularNumbers().copy(color = colors.onSurface, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (kind == NumberFieldKind.Decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        onKeyboardAction = {
            if (imeAction == ImeAction.Next) focusManager.moveFocus(FocusDirection.Next) else focusManager.clearFocus()
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.onSurface),
        decorator = { innerTextField ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CellHeight)
                    .clip(shape)
                    .background(fill)
                    .border(OutlineWidth, outline, shape)
                    .padding(horizontal = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                if (state.text.isEmpty() && !focused) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                        color = if (isError) colors.error else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** Rejects any edit that would leave text the field can't hold, rather than trying to repair it. */
private class NumberInputTransformation(
    private val kind: NumberFieldKind,
    private val maxLength: Int,
    private val separators: Set<Char>,
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        val valid = text.length <= maxLength && when (kind) {
            NumberFieldKind.Whole, NumberFieldKind.Duration -> text.all { it.isDigit() }
            NumberFieldKind.Decimal -> {
                val separatorAt = text.indexOfFirst { it in separators }
                text.all { it.isDigit() || it in separators } &&
                    text.count { it in separators } <= 1 &&
                    (separatorAt < 0 || text.length - separatorAt - 1 <= MAX_DECIMALS)
            }
        }
        if (!valid) revertAllChanges()
    }
}

/** Shows the digits of a duration as a clock: "5" → 0:05, "130" → 1:30, "1500" → 15:00. */
private object DurationOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        when (length) {
            0 -> Unit
            1 -> insert(0, "0:0")
            2 -> insert(0, "0:")
            else -> insert(length - 2, ":")
        }
    }
}

private const val MAX_DECIMALS = 2
private const val SETTLE_MILLIS = 200
private val OutlineWidth = 2.dp
private val CellHeight = 48.dp

@ThemePreviews
@Composable
private fun NumberFieldPreview() {
    LiftBookPreview {
        Row(Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            NumberField(rememberTextFieldState("3"), "Sets", NumberFieldKind.Whole, Modifier.weight(1f), maxLength = 2)
            NumberField(rememberTextFieldState(""), "Reps", NumberFieldKind.Whole, Modifier.weight(1f))
            NumberField(rememberTextFieldState("82.5"), "Weight", NumberFieldKind.Decimal, Modifier.weight(1.6f), unit = "kg")
        }
    }
}

@ThemePreviews
@Composable
private fun NumberFieldCardioPreview() {
    LiftBookPreview {
        Row(Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            NumberField(rememberTextFieldState("0"), "Sets", NumberFieldKind.Whole, Modifier.weight(1f), isError = true)
            NumberField(rememberTextFieldState("2000"), "Time", NumberFieldKind.Duration, Modifier.weight(1.3f))
            NumberField(rememberTextFieldState(""), "Distance", NumberFieldKind.Decimal, Modifier.weight(1.3f), unit = "km")
        }
    }
}
