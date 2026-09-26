package com.example.liftbook.ui.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.example.liftbook.R
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.NumberField
import com.example.liftbook.ui.components.NumberFieldKind
import com.example.liftbook.ui.components.PastDatePickerDialog
import com.example.liftbook.ui.components.bodyWeightText
import com.example.liftbook.ui.components.weightLabelRes
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.LocalDate

/**
 * Logs a weigh-in, or changes one (FR-5.4): the day and the weight side by side, the weight ready
 * to type, and Save held above the keyboard. A day that already has a weigh-in says so, since
 * saving replaces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightLogSheet(
    state: BodyWeightLogState,
    weight: TextFieldState,
    weightUnit: WeightUnit,
    today: LocalDate,
    onChangeDate: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column {
            Text(
                text = stringResource(if (state.isEditing) R.string.body_weight_log_edit_title else R.string.body_weight_log_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter)
                    .semantics { heading() },
            )
            Row(
                Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                DateTile(
                    date = state.date,
                    today = today,
                    onClick = { pickingDate = true },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    state = weight,
                    label = stringResource(R.string.body_weight_log_weight),
                    kind = NumberFieldKind.Decimal,
                    unit = stringResource(weightUnit.weightLabelRes()),
                    isError = state.showError,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
            }
            val note = when {
                state.showError -> stringResource(R.string.body_weight_log_error)
                state.existing != null && !state.isEditing ->
                    stringResource(R.string.body_weight_log_replaces, bodyWeightText(state.existing.weightKg, weightUnit))
                else -> null
            }
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.showError) colors.error else colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs),
                )
            }
            if (state.isEditing) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                ) {
                    Text(stringResource(R.string.body_weight_log_delete))
                }
            }
            BottomActionBar(
                text = stringResource(R.string.body_weight_log_save),
                onClick = onSave,
                enabled = !state.isSaving,
                containerColor = colors.surfaceContainerLow,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        // Ready to type as soon as it opens: logging a weight is one number.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    if (pickingDate) {
        PastDatePickerDialog(
            selected = state.date,
            today = today,
            onPick = { date ->
                pickingDate = false
                onChangeDate(date)
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The weigh-in's day, drawn like the weight field beside it; it opens a date picker. */
@Composable
private fun DateTile(date: LocalDate, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.body_weight_log_date)
    val value = if (date == today) stringResource(R.string.date_today) else workoutDateText(date, today)
    val spoken = stringResource(R.string.body_weight_log_date_spoken, label, value)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .clickable(onClickLabel = stringResource(R.string.body_weight_log_date_click_label), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@ThemePreviews
@Composable
private fun DateTilePreview() {
    val today = LocalDate.of(2026, 9, 26)
    LiftBookPreview {
        Row(Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DateTile(date = today, today = today, onClick = {}, modifier = Modifier.weight(1f))
            NumberField(
                state = rememberTextFieldState("82.4"),
                label = "Weight",
                kind = NumberFieldKind.Decimal,
                unit = "kg",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun BodyWeightLogSheetPreview() {
    val today = LocalDate.of(2026, 9, 26)
    LiftBookPreview {
        BodyWeightLogSheet(
            state = BodyWeightLogState(date = today, existing = BodyWeightEntry("e", today, 82.4), isEditing = true),
            weight = rememberTextFieldState("82.4"),
            weightUnit = WeightUnit.KG,
            today = today,
            onChangeDate = {},
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}
