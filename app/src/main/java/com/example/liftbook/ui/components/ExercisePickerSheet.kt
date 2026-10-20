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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val muscleFilter: MuscleGroup? = null,
    val equipmentFilter: Equipment? = null,
    val libraryCount: Int = 0,
) {
    val hasFilters: Boolean get() = muscleFilter != null || equipmentFilter != null
    val isFiltering: Boolean get() = query.isNotEmpty() || hasFilters
}

/**
 * A full-height sheet for choosing exercises from the library: search and the muscle and
 * equipment filters at the top, results in the middle, and the confirm action at the bottom
 * where the thumb is. Several can be picked at once; each shows the number it will be added
 * as, so the order is visible before confirming. Narrowing is the caller's (see
 * searchExercises), so the sheet stays stateless; which filter's choices are open is all it
 * keeps for itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    state: ExercisePickerUiState,
    queryState: TextFieldState,
    onToggle: (exerciseId: String) -> Unit,
    onFilterByMuscle: (MuscleGroup?) -> Unit,
    onFilterByEquipment: (Equipment?) -> Unit,
    onClearFilters: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var openFilter by rememberSaveable { mutableStateOf<ExerciseFilterKind?>(null) }
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
                placeholder = pluralStringResource(R.plurals.exercises_search_placeholder, state.libraryCount, state.libraryCount),
                modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.md),
            )
            ExerciseFilterRow(
                muscleFilter = state.muscleFilter,
                equipmentFilter = state.equipmentFilter,
                resultCount = state.results.size,
                showCount = state.isFiltering,
                onOpenFilter = { openFilter = it },
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
            if (state.results.isEmpty()) {
                PickerEmpty(state = state, onClearFilters = onClearFilters, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    items(state.results, key = { it.id }, contentType = { "exercise" }) { exercise ->
                        val order = state.selectedIds.indexOf(exercise.id)
                        PickerRow(
                            exercise = exercise,
                            order = if (order >= 0) order + 1 else null,
                            onToggle = { onToggle(exercise.id) },
                        )
                    }
                }
            }
            BottomActionBar(
                text = if (state.selectedIds.isEmpty()) {
                    stringResource(R.string.picker_confirm_none)
                } else {
                    pluralStringResource(R.plurals.picker_confirm, state.selectedIds.size, state.selectedIds.size)
                },
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm() }
                },
                enabled = state.selectedIds.isNotEmpty(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }
    }

    // Opens above the picker; a short sheet, so the results stay in view behind it.
    openFilter?.let { kind ->
        ExerciseFilterSheet(
            kind = kind,
            muscleFilter = state.muscleFilter,
            equipmentFilter = state.equipmentFilter,
            onFilterByMuscle = onFilterByMuscle,
            onFilterByEquipment = onFilterByEquipment,
            onDismiss = { openFilter = null },
        )
    }
}

/**
 * Why nothing is listed, and the way out: a search with no match, filters that exclude
 * everything, or a library with every exercise archived. Filters can be cleared here; the
 * other two are fixed in Exercises, so the copy points there.
 */
@Composable
private fun PickerEmpty(
    state: ExercisePickerUiState,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasQuery = state.query.isNotEmpty()
    EmptyState(
        icon = if (state.isFiltering) Icons.Outlined.SearchOff else Icons.Outlined.Inventory2,
        title = when {
            hasQuery -> stringResource(R.string.exercises_empty_query_title, state.query)
            state.hasFilters -> stringResource(R.string.exercises_empty_filters_title)
            else -> stringResource(R.string.exercises_empty_library_title)
        },
        body = when {
            hasQuery && state.hasFilters -> stringResource(R.string.picker_empty_query_filters_body)
            hasQuery -> stringResource(R.string.picker_empty_body)
            state.hasFilters -> stringResource(R.string.exercises_empty_filters_body)
            else -> stringResource(R.string.picker_empty_library_body)
        },
        modifier = modifier,
        action = if (state.hasFilters) {
            {
                OutlinedButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.exercises_clear_filters))
                }
            }
        } else {
            null
        },
    )
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
        ExerciseIllustration(
            illustration = exercise.illustration,
            modifier = Modifier.size(ExerciseIllustrationDefaults.ThumbnailSize),
        )
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
    fun exercise(id: String, name: String, muscle: MuscleGroup, equipment: Equipment, illustration: String? = null) = Exercise(
        id, name, muscle, equipment, ExerciseType.STRENGTH,
        isCustom = false, isArchived = false, createdAt = Instant.EPOCH, illustration = illustration,
    )
    LiftBookPreview {
        Column {
            PickerRow(
                exercise("bench", "Bench Press (Barbell)", MuscleGroup.CHEST, Equipment.BARBELL, "barbell_bench_press"),
                order = 2,
                onToggle = {},
            )
            PickerRow(
                exercise("incline", "Incline Bench Press (Dumbbell)", MuscleGroup.CHEST, Equipment.DUMBBELL, "dumbbell_incline_bench_press"),
                order = null,
                onToggle = {},
            )
            PickerRow(
                exercise("ohp", "Overhead Press (Barbell)", MuscleGroup.SHOULDERS, Equipment.BARBELL, "barbell_standing_wide_military_press"),
                order = 1,
                onToggle = {},
            )
            PickerRow(exercise("y-raise", "Cable Y-Raise", MuscleGroup.SHOULDERS, Equipment.CABLE), order = null, onToggle = {})
        }
    }
}

@ThemePreviews
@Composable
private fun PickerEmptyFiltersPreview() {
    LiftBookPreview {
        PickerEmpty(
            state = ExercisePickerUiState(query = "zercher", muscleFilter = MuscleGroup.QUADS, libraryCount = 126),
            onClearFilters = {},
        )
    }
}
