package com.example.liftbook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.liftbook.R
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import kotlinx.coroutines.launch

/** Which of the two library filters (FR-1.4) is being chosen. */
enum class ExerciseFilterKind { Muscle, Equipment }

/**
 * The muscle and equipment filters: two dropdown chips, each named for its choice once one is
 * made, with the result count at the end while a search or a filter is narrowing the list.
 * Shared by the library and the exercise picker, so narrowing works the same wherever
 * exercises are found.
 */
@Composable
fun ExerciseFilterRow(
    muscleFilter: MuscleGroup?,
    equipmentFilter: Equipment?,
    resultCount: Int,
    showCount: Boolean,
    onOpenFilter: (ExerciseFilterKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownFilterChip(
            label = muscleFilter?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.exercises_filter_muscle),
            active = muscleFilter != null,
            onClick = { onOpenFilter(ExerciseFilterKind.Muscle) },
        )
        DropdownFilterChip(
            label = equipmentFilter?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.exercises_filter_equipment),
            active = equipmentFilter != null,
            onClick = { onOpenFilter(ExerciseFilterKind.Equipment) },
        )
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(
            visible = showCount,
            enter = fadeIn(tween(COUNT_FADE_MILLIS)),
            exit = fadeOut(tween(COUNT_FADE_MILLIS)),
        ) {
            Text(
                text = pluralStringResource(R.plurals.exercises_result_count, resultCount, resultCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Announces the count as the user types, so TalkBack users know a search worked.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** One filter's choices in a sheet: "any", then every option. A tap picks and closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseFilterSheet(
    kind: ExerciseFilterKind,
    muscleFilter: MuscleGroup?,
    equipmentFilter: Equipment?,
    onFilterByMuscle: (MuscleGroup?) -> Unit,
    onFilterByEquipment: (Equipment?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ExerciseFilterChoices(
            kind = kind,
            muscleFilter = muscleFilter,
            equipmentFilter = equipmentFilter,
            onFilterByMuscle = { muscle ->
                onFilterByMuscle(muscle)
                close()
            },
            onFilterByEquipment = { equipment ->
                onFilterByEquipment(equipment)
                close()
            },
            modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        )
    }
}

@Composable
private fun ExerciseFilterChoices(
    kind: ExerciseFilterKind,
    muscleFilter: MuscleGroup?,
    equipmentFilter: Equipment?,
    onFilterByMuscle: (MuscleGroup?) -> Unit,
    onFilterByEquipment: (Equipment?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = stringResource(
                when (kind) {
                    ExerciseFilterKind.Muscle -> R.string.exercises_filter_muscle_title
                    ExerciseFilterKind.Equipment -> R.string.exercises_filter_equipment_title
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.md))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            when (kind) {
                ExerciseFilterKind.Muscle -> {
                    ChoiceChip(
                        selected = muscleFilter == null,
                        onClick = { onFilterByMuscle(null) },
                        label = stringResource(R.string.exercises_filter_any_muscle),
                    )
                    MuscleGroup.entries.forEach { muscle ->
                        ChoiceChip(
                            selected = muscleFilter == muscle,
                            onClick = { onFilterByMuscle(muscle) },
                            label = stringResource(muscle.labelRes()),
                        )
                    }
                }
                ExerciseFilterKind.Equipment -> {
                    ChoiceChip(
                        selected = equipmentFilter == null,
                        onClick = { onFilterByEquipment(null) },
                        label = stringResource(R.string.exercises_filter_any_equipment),
                    )
                    Equipment.entries.forEach { equipment ->
                        ChoiceChip(
                            selected = equipmentFilter == equipment,
                            onClick = { onFilterByEquipment(equipment) },
                            label = stringResource(equipment.labelRes()),
                        )
                    }
                }
            }
        }
    }
}

private const val COUNT_FADE_MILLIS = 200

@ThemePreviews
@Composable
private fun ExerciseFilterRowPreview() {
    LiftBookPreview {
        Column {
            ExerciseFilterRow(
                muscleFilter = null,
                equipmentFilter = null,
                resultCount = 129,
                showCount = false,
                onOpenFilter = {},
                modifier = Modifier.padding(Spacing.gutter),
            )
            ExerciseFilterRow(
                muscleFilter = MuscleGroup.CHEST,
                equipmentFilter = Equipment.BARBELL,
                resultCount = 6,
                showCount = true,
                onOpenFilter = {},
                modifier = Modifier.padding(Spacing.gutter),
            )
        }
    }
}

// A modal sheet renders in its own window, which previews don't show; the choices are the part to check.
@ThemePreviews
@Composable
private fun ExerciseFilterChoicesPreview() {
    LiftBookPreview {
        ExerciseFilterChoices(
            kind = ExerciseFilterKind.Muscle,
            muscleFilter = MuscleGroup.BACK,
            equipmentFilter = null,
            onFilterByMuscle = {},
            onFilterByEquipment = {},
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}
