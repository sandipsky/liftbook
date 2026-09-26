package com.example.liftbook.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.liftbook.R
import com.example.liftbook.domain.model.BackupSummary
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.workoutDateText
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A backup, read and checked, before anything is imported (FR-6.4): when it was made, what's in
 * it, and the choice between merging it in and replacing this phone's data with it. Merge is
 * the safe choice and comes first. The action names what it will do, and a replace still asks
 * once more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBackupSheet(
    draft: ImportDraft,
    isImporting: Boolean,
    today: LocalDate,
    zone: ZoneId,
    onChooseMode: (ImportMode) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val importing by rememberUpdatedState(isImporting)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // An import under way finishes with the sheet still up, saying so.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !importing }),
        containerColor = colors.surfaceContainerLow,
    ) {
        Column {
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Text(
                    text = stringResource(R.string.import_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(
                        R.string.import_exported_on,
                        workoutDateText(draft.summary.exportedAt.atZone(zone).toLocalDate(), today),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                DataCountTiles(counts = draft.summary.counts, modifier = Modifier.padding(top = Spacing.md))
            }
            Column(
                Modifier
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = Spacing.lg)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ImportModeOption(
                    title = stringResource(R.string.import_merge_title),
                    body = stringResource(R.string.import_merge_body),
                    selected = draft.mode == ImportMode.MERGE,
                    enabled = !isImporting,
                    onClick = { onChooseMode(ImportMode.MERGE) },
                )
                ImportModeOption(
                    title = stringResource(R.string.import_replace_title),
                    body = stringResource(R.string.import_replace_body),
                    selected = draft.mode == ImportMode.REPLACE,
                    enabled = !isImporting,
                    onClick = { onChooseMode(ImportMode.REPLACE) },
                )
            }
            BottomActionBar(
                text = stringResource(
                    when {
                        isImporting -> R.string.import_in_progress
                        draft.mode == ImportMode.MERGE -> R.string.import_merge_action
                        else -> R.string.import_replace_action
                    },
                ),
                onClick = onImport,
                enabled = !isImporting,
                containerColor = colors.surfaceContainerLow,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

/** One way to import: selection shown by an ink radio, as elsewhere, on a tonal tile. */
@Composable
private fun ImportModeOption(title: String, body: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHigh)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = Spacing.xs, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = colors.onSurface),
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.rowTitle, color = colors.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@ThemePreviews
@Composable
private fun ImportModeOptionPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ImportModeOption(title = "Merge", body = "Adds what isn’t on this phone yet. Nothing here changes.", selected = true, enabled = true, onClick = {})
            ImportModeOption(title = "Replace", body = "Deletes everything here first.", selected = false, enabled = true, onClick = {})
        }
    }
}

@ThemePreviews
@Composable
private fun ImportBackupSheetPreview() {
    LiftBookPreview {
        ImportBackupSheet(
            draft = ImportDraft(
                source = DocumentUri("content://preview"),
                summary = BackupSummary(Instant.parse("2026-09-12T08:00:00Z"), DataCounts(workouts = 128, routines = 5, customExercises = 3, weighIns = 28)),
                mode = ImportMode.MERGE,
            ),
            isImporting = false,
            today = LocalDate.of(2026, 9, 26),
            zone = ZoneOffset.UTC,
            onChooseMode = {},
            onImport = {},
            onDismiss = {},
        )
    }
}
