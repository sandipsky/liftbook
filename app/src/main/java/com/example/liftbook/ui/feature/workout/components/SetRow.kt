package com.example.liftbook.ui.feature.workout.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.ui.components.NumberCell
import com.example.liftbook.ui.components.NumberFieldKind
import com.example.liftbook.ui.components.distanceLabelRes
import com.example.liftbook.ui.components.setMarker
import com.example.liftbook.ui.components.setSpokenTitle
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.feature.workout.ActiveSet
import com.example.liftbook.ui.feature.workout.SetFields
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers

/**
 * The column labels above a set table: Set · kg · Reps, Set · Reps, or Set · Time · km — the
 * fields each row logs (FR-3.3). Hidden from TalkBack; each cell carries its own label. Leave
 * out [showDoneColumn] when the rows have no done toggle.
 */
@Composable
fun SetTableHeader(type: ExerciseType, weightUnit: WeightUnit, modifier: Modifier = Modifier, showDoneColumn: Boolean = true) {
    val labels = when (type) {
        ExerciseType.STRENGTH -> listOf(stringResource(weightUnit.weightLabelRes()), stringResource(R.string.workout_column_reps))
        ExerciseType.BODYWEIGHT -> listOf(stringResource(R.string.workout_column_reps))
        ExerciseType.CARDIO -> listOf(stringResource(R.string.workout_column_time), stringResource(weightUnit.distanceLabelRes()))
    }
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ColumnLabel(stringResource(R.string.workout_column_set), Modifier.width(ControlSize))
        labels.forEach { ColumnLabel(it, Modifier.weight(1f)) }
        if (showDoneColumn) Spacer(Modifier.width(ControlSize))
    }
}

@Composable
private fun ColumnLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.padding(vertical = Spacing.xxs),
    )
}

/**
 * One set: its type, the values it logs, and the control that marks it done — the most-used
 * control in the app, so every part of it is a 48dp target a sweaty thumb can't miss.
 *
 * The values are real, editable fields pre-filled from last time (FR-3.4). Marking the set done
 * settles them: the fill fades and the numbers stand on the row, still editable. Trying to mark
 * it done with a value missing buzzes and outlines what's missing instead.
 *
 * Without [onToggleDone] — a finished workout being edited, where every set is done — there is
 * no toggle, and the values keep their fill, ready to edit.
 */
@Composable
fun SetRow(
    item: ActiveSet,
    type: ExerciseType,
    weightUnit: WeightUnit,
    onToggleDone: (() -> Unit)?,
    onChangeType: (SetType) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val set = item.set
    val fields = item.fields
    val title = setSpokenTitle(set.setType, item.number)
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val missing = stringResource(if (onToggleDone != null) R.string.set_missing_values else R.string.set_missing_values_edit)
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SetTypeButton(
            setType = set.setType,
            number = item.number,
            spokenTitle = title,
            onChangeType = onChangeType,
            onRemove = onRemove,
        )
        val settled = onToggleDone != null && set.isCompleted
        when (type) {
            ExerciseType.STRENGTH -> {
                NumberCell(
                    state = fields.weight.state,
                    kind = NumberFieldKind.Decimal,
                    contentDescription = stringResource(R.string.set_field_weight, title, stringResource(weightUnit.weightLabelRes())),
                    isError = item.showMissing && fields.weight.value() == null,
                    errorDescription = missing,
                    settled = settled,
                    modifier = Modifier.weight(1f),
                )
                NumberCell(
                    state = fields.reps.state,
                    kind = NumberFieldKind.Whole,
                    contentDescription = stringResource(R.string.set_field_reps, title),
                    isError = item.showMissing && (fields.reps.value() ?: 0) <= 0,
                    errorDescription = missing,
                    settled = settled,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
            ExerciseType.BODYWEIGHT -> NumberCell(
                state = fields.reps.state,
                kind = NumberFieldKind.Whole,
                contentDescription = stringResource(R.string.set_field_reps, title),
                isError = item.showMissing && (fields.reps.value() ?: 0) <= 0,
                errorDescription = missing,
                settled = settled,
                imeAction = ImeAction.Done,
                modifier = Modifier.weight(1f),
            )
            ExerciseType.CARDIO -> {
                NumberCell(
                    state = fields.duration.state,
                    kind = NumberFieldKind.Duration,
                    contentDescription = stringResource(R.string.set_field_time, title),
                    isError = item.showMissing && (fields.duration.value() ?: 0) <= 0,
                    errorDescription = missing,
                    settled = settled,
                    modifier = Modifier.weight(1f),
                )
                NumberCell(
                    state = fields.distance.state,
                    kind = NumberFieldKind.Decimal,
                    contentDescription = stringResource(R.string.set_field_distance, title, stringResource(weightUnit.distanceLabelRes())),
                    settled = settled,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (onToggleDone != null) DoneToggle(
            done = set.isCompleted,
            isRecord = set.isCompleted && item.records.isNotEmpty(),
            label = stringResource(R.string.set_done_label, title),
            onToggle = {
                val completing = !set.isCompleted
                val complete = fields.values().applicableTo(type).metricsFor(type) != null
                haptics.performHapticFeedback(
                    when {
                        !completing -> HapticFeedbackType.ToggleOff
                        complete -> HapticFeedbackType.Confirm
                        else -> HapticFeedbackType.Reject
                    },
                )
                // Done with the set is done with typing: the keyboard goes and the values settle.
                if (!completing || complete) focusManager.clearFocus()
                onToggleDone()
            },
        )
    }
}

/**
 * The set's marker — its number, or W / D / F — which opens the set types (FR-3.10) and Remove.
 * A normal set shows its number plainly; any other type sits in a small tonal disc, so it reads
 * by shape and letter as well as by tone.
 */
@Composable
private fun SetTypeButton(
    setType: SetType,
    number: Int?,
    spokenTitle: String,
    onChangeType: (SetType) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val clickLabel = stringResource(R.string.set_type_click_label)
    Box {
        Box(
            modifier = Modifier
                .size(ControlSize)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = clickLabel) { expanded = true }
                .semantics { contentDescription = spokenTitle },
            contentAlignment = Alignment.Center,
        ) {
            val special = setType != SetType.NORMAL
            Box(
                modifier = Modifier
                    .size(MarkerDiscSize)
                    .clip(CircleShape)
                    .background(if (special) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = setMarker(setType, number),
                    style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                    color = if (special) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SetTypeOrder.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes())) },
                    leadingIcon = {
                        Box(Modifier.size(IconSize.action), contentAlignment = Alignment.Center) {
                            if (type == setType) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(IconSize.inline))
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (type != setType) onChangeType(type)
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xxs))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_remove)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * Marks the set done. Undone it's an outlined square holding a quiet check; done it fills with
 * the accent — completing a set is a progress moment — with a short settle, so the change is
 * felt rather than watched. The check is there either way, so the state never rests on colour.
 *
 * A done set that sets a personal record (FR-5.2) trades its check for a trophy, cross-faded in:
 * the record lands where the thumb just was, and needs no room of its own on the row. TalkBack
 * hears it in the toggle's state.
 */
@Composable
private fun DoneToggle(done: Boolean, isRecord: Boolean, label: String, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (done) colors.primary else Color.Transparent,
        animationSpec = tween(TOGGLE_MILLIS),
        label = "doneFill",
    )
    val content by animateColorAsState(
        targetValue = if (done) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = tween(TOGGLE_MILLIS),
        label = "doneContent",
    )
    val scale = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done) {
            scale.snapTo(SETTLE_FROM_SCALE)
            scale.animateTo(1f, tween(TOGGLE_MILLIS))
        }
    }
    val shape = MaterialTheme.shapes.medium
    val recordState = stringResource(R.string.set_done_record_state)
    Box(
        modifier = Modifier
            .size(ControlSize)
            .clip(shape)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics {
                contentDescription = label
                if (isRecord) stateDescription = recordState
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ToggleSize)
                .scale(scale.value)
                .clip(shape)
                .background(fill)
                .border(ToggleOutline, if (done) Color.Transparent else colors.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = isRecord, animationSpec = tween(TOGGLE_MILLIS), label = "doneIcon") { showRecord ->
                Icon(
                    if (showRecord) Icons.Outlined.EmojiEvents else Icons.Outlined.Check,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(IconSize.action),
                )
            }
        }
    }
}

private fun SetType.labelRes(): Int = when (this) {
    SetType.NORMAL -> R.string.set_type_normal
    SetType.WARMUP -> R.string.set_type_warmup
    SetType.DROP -> R.string.set_type_drop
    SetType.FAILURE -> R.string.set_type_failure
}

/** The order set types are offered in: the usual first, then warm-up before a set, then after. */
private val SetTypeOrder = listOf(SetType.NORMAL, SetType.WARMUP, SetType.DROP, SetType.FAILURE)

/** The set-type button and the done toggle: the minimum touch target. */
internal val ControlSize = 48.dp
private val ToggleSize = 40.dp
private val MarkerDiscSize = 32.dp
private val ToggleOutline = 2.dp
private const val TOGGLE_MILLIS = 180
private const val SETTLE_FROM_SCALE = 0.86f

@ThemePreviews
@Composable
private fun SetRowPreview() {
    fun set(id: String, type: SetType, done: Boolean, weight: Double?, reps: Int?, number: Int?, missing: Boolean = false, record: Boolean = false) =
        ActiveSet(
            set = WorkoutSet(id = id, setType = type, isCompleted = done, weightKg = weight, reps = reps),
            number = number,
            fields = SetFields.from(SetValues(weightKg = weight, reps = reps), WeightUnit.KG),
            showMissing = missing,
            records = if (record) listOf(PersonalRecord.HeaviestWeight(weight ?: 0.0, reps ?: 0)) else emptyList(),
        )
    LiftBookPreview {
        Column(Modifier.padding(Spacing.xs)) {
            SetTableHeader(ExerciseType.STRENGTH, WeightUnit.KG)
            listOf(
                set("w", SetType.WARMUP, done = true, weight = 40.0, reps = 10, number = null),
                set("1", SetType.NORMAL, done = true, weight = 80.0, reps = 8, number = 1),
                set("r", SetType.NORMAL, done = true, weight = 85.0, reps = 6, number = 2, record = true),
                set("2", SetType.NORMAL, done = false, weight = 82.5, reps = 8, number = 3),
                set("3", SetType.FAILURE, done = false, weight = null, reps = null, number = 4, missing = true),
            ).forEach { item ->
                SetRow(
                    item = item,
                    type = ExerciseType.STRENGTH,
                    weightUnit = WeightUnit.KG,
                    onToggleDone = {},
                    onChangeType = {},
                    onRemove = {},
                )
            }
        }
    }
}
