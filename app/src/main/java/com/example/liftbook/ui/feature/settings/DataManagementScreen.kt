package com.example.liftbook.ui.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.core.format.formatDecimal
import com.example.liftbook.domain.model.BackupProblem
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.Fact
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.StatTile
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DataManagementRoute(
    onNavigateUp: () -> Unit,
    viewModel: DataManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    // The system file picker: no storage permission, and the file can go anywhere a provider reaches.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.onAction(DataManagementAction.ExportTo(DocumentUri(uri.toString())))
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onAction(DataManagementAction.ReadBackup(DocumentUri(uri.toString())))
    }
    // A stripped-down phone may have no file picker at all.
    val launchPicker: (() -> Unit) -> Unit = { launch ->
        try {
            launch()
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.data_no_file_picker)) }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is DataManagementEvent.Exported -> if (event.counts.workouts > 0) {
                    resources.getQuantityString(R.plurals.data_exported, event.counts.workouts, event.counts.workouts)
                } else {
                    resources.getString(R.string.data_exported_empty)
                }
                DataManagementEvent.ExportFailed -> resources.getString(R.string.data_export_failed)
                is DataManagementEvent.CantImport -> resources.getString(event.problem.messageRes())
                is DataManagementEvent.Merged -> if (event.added.isEmpty) {
                    resources.getString(R.string.import_merged_nothing)
                } else {
                    resources.getString(R.string.import_merged, resources.dataCountsText(event.added))
                }
                DataManagementEvent.Replaced -> resources.getString(R.string.import_replaced)
                DataManagementEvent.ImportFailed -> resources.getString(R.string.import_failed)
                DataManagementEvent.Cleared -> resources.getString(R.string.data_cleared)
                DataManagementEvent.ClearFailed -> resources.getString(R.string.data_clear_failed)
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    DataManagementScreen(
        state = state,
        clearConfirmation = viewModel.clearConfirmation,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                DataManagementAction.NavigateUp -> onNavigateUp()
                DataManagementAction.Export -> launchPicker { exportLauncher.launch(state.backupFileName) }
                DataManagementAction.Import -> launchPicker { importLauncher.launch(BACKUP_PICK_TYPES) }
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Backup & data (FR-6.3–6.5). With no cloud, a backup file is the only copy of a user's history
 * that can outlive the phone, so the screen leads with what's on it and when it was last backed
 * up, and Export is its one primary action. Importing and clearing sit below as rows; clearing,
 * the one that can't be undone, sits last and apart. An empty phone has nothing to export, so it
 * offers the import that fills it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    state: DataManagementUiState,
    clearConfirmation: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (DataManagementAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val hasData = !state.isLoading && !state.counts.isEmpty

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.data_title),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(DataManagementAction.NavigateUp) },
                showTitle = headlineScrolledAway,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (hasData) {
                BottomActionBar(
                    text = stringResource(if (state.operation == BackupOperation.EXPORTING) R.string.data_exporting else R.string.data_export),
                    onClick = { onAction(DataManagementAction.Export) },
                    enabled = !state.isBusy,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = state.isLoading,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "dataContent",
        ) { loading ->
            if (loading) {
                DataManagementSkeleton(Modifier.padding(padding))
            } else {
                DataManagementList(state, listState, padding, onAction)
            }
        }
    }

    state.importing?.let { draft ->
        ImportBackupSheet(
            draft = draft,
            isImporting = state.operation == BackupOperation.IMPORTING,
            today = state.today,
            zone = state.zone,
            onChooseMode = { onAction(DataManagementAction.ChooseImportMode(it)) },
            onImport = { onAction(DataManagementAction.StartImport) },
            onDismiss = { onAction(DataManagementAction.DismissImport) },
        )
    }

    if (state.isConfirmingReplace) {
        val workouts = state.counts.workouts
        ConfirmDialog(
            title = stringResource(R.string.import_replace_dialog_title),
            text = if (workouts > 0) {
                pluralStringResource(R.plurals.import_replace_dialog_body, workouts, workouts)
            } else {
                stringResource(R.string.import_replace_dialog_body_no_workouts)
            },
            confirmLabel = stringResource(R.string.import_replace_dialog_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { onAction(DataManagementAction.ConfirmReplace) },
            onDismiss = { onAction(DataManagementAction.DismissReplace) },
            destructive = true,
        )
    }

    if (state.isConfirmingClear) {
        ClearDataDialog(
            confirmation = clearConfirmation,
            lastExportedAt = state.lastExportedAt,
            today = state.today,
            zone = state.zone,
            onConfirm = { onAction(DataManagementAction.ConfirmClear) },
            onDismiss = { onAction(DataManagementAction.DismissClear) },
        )
    }
}

@Composable
private fun DataManagementList(
    state: DataManagementUiState,
    listState: LazyListState,
    padding: PaddingValues,
    onAction: (DataManagementAction) -> Unit,
) {
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(key = "header", contentType = "header") {
            Column(gutter.padding(top = Spacing.xs, bottom = Spacing.md)) {
                Text(
                    text = stringResource(R.string.data_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.data_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.counts.isEmpty) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.Outlined.SettingsBackupRestore,
                    title = stringResource(R.string.data_empty_title),
                    body = stringResource(R.string.data_empty_body),
                    action = {
                        Button(onClick = { onAction(DataManagementAction.Import) }, enabled = !state.isBusy) {
                            Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(IconSize.inline))
                            Spacer(Modifier.size(Spacing.xs))
                            Text(stringResource(R.string.import_action))
                        }
                    },
                )
            }
            return@LazyColumn
        }

        item(key = "onPhone", contentType = "panel") {
            OnThisPhonePanel(state, gutter.padding(bottom = Spacing.md))
        }
        item(key = "import", contentType = "row") {
            SettingsRow(
                title = stringResource(R.string.import_action),
                supporting = stringResource(R.string.import_row_body),
                icon = Icons.Outlined.SettingsBackupRestore,
                onClick = { onAction(DataManagementAction.Import) },
                clickLabel = stringResource(R.string.import_click_label),
                enabled = !state.isBusy,
                modifier = gutter,
            )
        }
        item(key = "clear", contentType = "row") {
            SettingsRow(
                title = stringResource(R.string.data_clear_title),
                supporting = stringResource(R.string.data_clear_body),
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = { onAction(DataManagementAction.ClearAll) },
                clickLabel = stringResource(R.string.data_clear_click_label),
                enabled = !state.isBusy,
                modifier = gutter.padding(top = Spacing.lg),
            )
        }
    }
}

/** What's at stake: this phone's history in numbers, and when it was last backed up. */
@Composable
private fun OnThisPhonePanel(state: DataManagementUiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val lastBackup = state.lastExportedAt
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.data_on_phone),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        DataCountTiles(counts = state.counts, modifier = Modifier.padding(top = Spacing.sm))
        Fact(
            label = stringResource(R.string.data_last_backup),
            value = if (lastBackup != null) backupDayText(lastBackup, state.today, state.zone) else stringResource(R.string.data_last_backup_never),
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

/** Workouts, routines and weigh-ins, as numbers first — for this phone, or for a backup. */
@Composable
fun DataCountTiles(counts: DataCounts, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatTile(label = stringResource(R.string.data_workouts), value = countText(counts.workouts), modifier = Modifier.weight(1f))
        StatTile(label = stringResource(R.string.data_routines), value = countText(counts.routines), modifier = Modifier.weight(1f))
        StatTile(label = stringResource(R.string.data_weigh_ins), value = countText(counts.weighIns), modifier = Modifier.weight(1f))
    }
}

private fun countText(count: Int): String = formatDecimal(count.toDouble(), maxFractionDigits = 0)

private fun BackupProblem.messageRes(): Int = when (this) {
    BackupProblem.UNREADABLE -> R.string.import_problem_unreadable
    BackupProblem.NOT_A_BACKUP -> R.string.import_problem_not_backup
    BackupProblem.NEWER_VERSION -> R.string.import_problem_newer
    BackupProblem.DAMAGED -> R.string.import_problem_damaged
}

@Composable
private fun DataManagementSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(contentDescription = stringResource(R.string.data_loading), modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(Spacing.xl))
            SkeletonBlock(Modifier.padding(bottom = Spacing.md).fillMaxWidth().height(Spacing.xl))
            SkeletonBlock(Modifier.padding(bottom = Spacing.md).fillMaxWidth().height(SkeletonPanelHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonRowHeight), MaterialTheme.shapes.large)
        }
    }
}

/** Backups are JSON. Some providers label a .json file as plain text or bytes, so those can be picked too. */
private const val BACKUP_MIME_TYPE = "application/json"
private val BACKUP_PICK_TYPES = arrayOf(BACKUP_MIME_TYPE, "text/plain", "application/octet-stream")

private val SkeletonPanelHeight = 160.dp
private val SkeletonRowHeight = 64.dp
private const val CONTENT_FADE_MILLIS = 200

private val previewToday = LocalDate.of(2026, 9, 26)

@ThemePreviews
@Composable
private fun DataManagementScreenPreview() {
    LiftBookTheme {
        DataManagementScreen(
            state = DataManagementUiState(
                isLoading = false,
                counts = DataCounts(workouts = 142, routines = 6, customExercises = 4, weighIns = 31),
                lastExportedAt = Instant.parse("2026-09-12T08:00:00Z"),
                today = previewToday,
                zone = ZoneOffset.UTC,
            ),
            clearConfirmation = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun DataManagementScreenEmptyPreview() {
    LiftBookTheme {
        DataManagementScreen(
            state = DataManagementUiState(isLoading = false, today = previewToday, zone = ZoneOffset.UTC),
            clearConfirmation = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun DataManagementScreenLoadingPreview() {
    LiftBookTheme {
        DataManagementScreen(
            state = DataManagementUiState(today = previewToday),
            clearConfirmation = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
