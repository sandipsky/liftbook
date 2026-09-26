package com.example.liftbook.ui.feature.workout.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.NoteField
import com.example.liftbook.ui.components.ReorderableListState
import com.example.liftbook.ui.components.dragHandle
import com.example.liftbook.ui.components.exerciseMetaSpoken
import com.example.liftbook.ui.components.exerciseMetaText
import com.example.liftbook.ui.components.restDurationSpoken
import com.example.liftbook.ui.components.restDurationText
import com.example.liftbook.ui.feature.workout.ActiveExercise
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.rowTitle
import com.example.liftbook.ui.theme.tabularNumbers

/**
 * One exercise in the workout: its name, its rest time, an optional note (FR-3.9) and its sets
 * as a table to log into (FR-3.3). The block is a single tonal surface; the table inside it has
 * no rules or boxes, only the fields themselves. It grows and shrinks smoothly as sets come and
 * go.
 */
@Composable
fun ExerciseBlock(
    item: ActiveExercise,
    weightUnit: WeightUnit,
    onToggleDone: (setId: String) -> Unit,
    onChangeSetType: (setId: String, SetType) -> Unit,
    onRemoveSet: (setId: String) -> Unit,
    onAddSet: () -> Unit,
    onEditRest: () -> Unit,
    onAddNote: () -> Unit,
    onReorder: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val exercise = item.item.exercise
    val metaSpoken = exerciseMetaSpoken(exercise)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .animateContentSize(tween(RESIZE_MILLIS))
            .padding(bottom = Spacing.xxs),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xxs, top = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = exerciseMetaText(exercise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = metaSpoken },
                )
            }
            RestChip(seconds = item.restSeconds, onClick = onEditRest)
            ExerciseMenu(
                exerciseName = exercise.name,
                canAddNote = !item.showNote,
                onAddNote = onAddNote,
                onReorder = onReorder,
                onRemove = onRemove,
            )
        }
        if (item.showNote) {
            NoteField(
                state = item.note,
                placeholder = stringResource(R.string.workout_exercise_note_placeholder),
                label = stringResource(R.string.workout_exercise_note_label, exercise.name),
                containerColor = colors.surfaceContainerHigh,
                // Asked for just now, with nothing in it yet: ready to type.
                autoFocus = remember { item.note.text.isEmpty() },
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.xxs, bottom = Spacing.xs),
            )
        }
        Column(Modifier.padding(horizontal = Spacing.xs)) {
            SetTableHeader(type = exercise.type, weightUnit = weightUnit)
            item.sets.forEach { set ->
                SetRow(
                    item = set,
                    type = exercise.type,
                    weightUnit = weightUnit,
                    onToggleDone = { onToggleDone(set.set.id) },
                    onChangeType = { onChangeSetType(set.set.id, it) },
                    onRemove = { onRemoveSet(set.set.id) },
                )
            }
        }
        TextButton(
            onClick = onAddSet,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs)
                .heightIn(min = ControlSize),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
        ) {
            val description = stringResource(R.string.workout_add_set_description, exercise.name)
            Row(
                Modifier.clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(R.string.workout_add_set), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * How long the rest after each set is — tap to change it (FR-3.5). A small tonal pill beside
 * the name: where the user looks for it, quiet until needed.
 */
@Composable
private fun RestChip(seconds: Int, onClick: () -> Unit) {
    val spoken = stringResource(R.string.workout_exercise_rest_spoken, restDurationSpoken(seconds))
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.workout_exercise_rest_click_label), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.xxs, bottom = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Icon(
                Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.inline),
            )
            Text(
                text = restDurationText(seconds),
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ExerciseMenu(
    exerciseName: String,
    canAddNote: Boolean,
    onAddNote: () -> Unit,
    onReorder: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.workout_exercise_options, exerciseName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canAddNote) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workout_exercise_add_note)) },
                    leadingIcon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAddNote()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_menu_reorder)) },
                leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                onClick = {
                    expanded = false
                    onReorder()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_exercise_remove)) },
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
 * An exercise while reordering (FR-3.2): just its name and set count, so a whole workout fits
 * on screen to drag through. Lifted while dragged — a tone up and a shadow, the one place a
 * shadow means something. The handle can't be used from TalkBack, so the row offers Move up and
 * Move down as accessibility actions instead.
 */
@Composable
fun ReorderRow(
    item: ActiveExercise,
    reorder: ReorderableListState,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val container by animateColorAsState(
        targetValue = if (isDragging) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        animationSpec = tween(LIFT_MILLIS),
        label = "reorderRowColor",
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) LiftedElevation else 0.dp,
        animationSpec = tween(LIFT_MILLIS),
        label = "reorderRowElevation",
    )
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    val setCount = pluralStringResource(R.plurals.sets_count, item.sets.size, item.sets.size)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(elevation, shape)
            .clip(shape)
            .background(container)
            .semantics(mergeDescendants = true) {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(moveUp) { onMove(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction(moveDown) { onMove(1); true })
                }
            }
            .padding(end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ControlSize + Spacing.xs)
                .dragHandle(reorder, item.id)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DragIndicator, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(IconSize.action))
        }
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(
                text = item.item.exercise.name,
                style = MaterialTheme.typography.rowTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = setCount, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

private val LiftedElevation = 8.dp
private const val RESIZE_MILLIS = 200
private const val LIFT_MILLIS = 150
