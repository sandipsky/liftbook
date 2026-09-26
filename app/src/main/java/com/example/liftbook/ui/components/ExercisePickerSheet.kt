package com.example.liftbook.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle
import com.example.liftbook.ui.theme.tabularNumbers
import kotlinx.coroutines.launch
import java.time.Instant

/** What an [ExercisePickerSheet] shows while it's open. */
data class ExercisePickerUiState(
    val results: List<Exercise> = emptyList(),
    /** In the order they were picked, which is the order they'll be added. */
    val selectedIds: List<String> = emptyList(),
    /** The search text as last applied, trimmed. */
    val query: String = "",
    val libraryCount: Int = 0,
)

/**
 * A full-height sheet for choosing exercises from the library: search at the top, results in
 * the middle, and the confirm action at the bottom where the thumb is. Several can be picked
 * at once; each shows the number it will be added as, so the order is visible before
 * confirming. Filtering is the caller's (see searchExercises), so the sheet stays stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    results: List<Exercise>,
    selectedIds: List<String>,
    query: String,
    queryState: TextFieldState,
    libraryCount: Int,
    onToggle: (exerciseId: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxHeight()) {
            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter)
                    .semantics { heading() },
            )
            SearchField(
                state = queryState,
                placeholder = pluralStringResource(R.plurals.exercises_search_placeholder, libraryCount, libraryCount),
                modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.md, bottom = Spacing.xs),
            )
            if (results.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.exercises_empty_query_title, query),
                    body = stringResource(R.string.picker_empty_body),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    items(results, key = { it.id }, contentType = { "exercise" }) { exercise ->
                        val order = selectedIds.indexOf(exercise.id)
                        PickerRow(
                            exercise = exercise,
                            order = if (order >= 0) order + 1 else null,
                            onToggle = { onToggle(exercise.id) },
                        )
                    }
                }
            }
            BottomActionBar(
                text = if (selectedIds.isEmpty()) {
                    stringResource(R.string.picker_confirm_none)
                } else {
                    pluralStringResource(R.plurals.picker_confirm, selectedIds.size, selectedIds.size)
                },
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm() }
                },
                enabled = selectedIds.isNotEmpty(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }
    }
}

@Composable
private fun PickerRow(exercise: Exercise, order: Int?, onToggle: () -> Unit) {
    val selected = order != null
    val state = if (order != null) {
        stringResource(R.string.picker_selected, order)
    } else {
        stringResource(R.string.picker_not_selected)
    }
    val metaSpoken = exerciseMetaSpoken(exercise)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = state }
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = exerciseMetaText(exercise),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = metaSpoken },
            )
        }
        SelectionMark(order)
    }
}

/**
 * An empty ring, or — once picked — an ink disc holding the pick's number. Ink rather than the
 * accent, as for chips; the number and the fill change together, so it never rests on colour.
 */
@Composable
private fun SelectionMark(order: Int?) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (order != null) colors.inverseSurface else Color.Transparent,
        animationSpec = tween(MARK_FADE_MILLIS),
        label = "selectionFill",
    )
    Box(
        modifier = Modifier
            .size(IconSize.action)
            .clip(CircleShape)
            .background(fill)
            .border(RingWidth, if (order != null) colors.inverseSurface else colors.outline, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (order != null) {
            Text(
                text = order.toString(),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = colors.inverseOnSurface,
            )
        }
    }
}

private val RingWidth = 2.dp
private const val MARK_FADE_MILLIS = 150

// A modal sheet renders in its own window, which previews don't show; the rows are the part to check.
@ThemePreviews
@Composable
private fun PickerRowsPreview() {
    fun exercise(id: String, name: String, muscle: MuscleGroup, equipment: Equipment) =
        Exercise(id, name, muscle, equipment, ExerciseType.STRENGTH, isCustom = false, isArchived = false, createdAt = Instant.EPOCH)
    LiftBookPreview {
        Column {
            PickerRow(exercise("bench", "Bench Press (Barbell)", MuscleGroup.CHEST, Equipment.BARBELL), order = 2, onToggle = {})
            PickerRow(exercise("incline", "Incline Bench Press (Dumbbell)", MuscleGroup.CHEST, Equipment.DUMBBELL), order = null, onToggle = {})
            PickerRow(exercise("ohp", "Overhead Press (Barbell)", MuscleGroup.SHOULDERS, Equipment.BARBELL), order = 1, onToggle = {})
        }
    }
}
