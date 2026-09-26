package com.example.liftbook.ui.feature.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.DialogOption
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.OptionDialog
import com.example.liftbook.ui.components.SettingsRow
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TimeOfDayPickerDialog
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun ReminderEditorRoute(
    scheduleId: String?,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = hiltViewModel<ReminderEditorViewModel, ReminderEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(scheduleId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // The reminders screen observes the schedule, so it already shows the change.
                ReminderEditorEvent.Saved, ReminderEditorEvent.Deleted -> currentOnDone()
                ReminderEditorEvent.SaveFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.reminder_editor_save_failed))
            }
        }
    }

    ReminderEditorScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action -> if (action == ReminderEditorAction.Close) onClose() else viewModel.onAction(action) },
    )
}

/**
 * A scheduled workout (FR-7.1, FR-7.2): the time it starts, largest, since it's what a reminder
 * is about; the days it's on; then what it's for and when to be reminded. Save sits at the
 * bottom, in thumb reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorScreen(
    state: ReminderEditorUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (ReminderEditorAction) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<EditorPicker?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val close = { if (state.hasUnsavedChanges) confirmDiscard = true else onAction(ReminderEditorAction.Close) }
    BackHandler(enabled = state.hasUnsavedChanges) { confirmDiscard = true }
    val ready = !state.isLoading && !state.isUnavailable

    Scaffold(
        topBar = {
            LiftBookTopBar(
                title = stringResource(if (state.isEditing) R.string.reminder_editor_edit else R.string.reminder_editor_new),
                navigation = TopBarNavigation.Close,
                onNavigationClick = close,
                actions = {
                    if (state.isEditing && ready) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.isSaving) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.reminder_editor_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (ready) {
                BottomActionBar(
                    text = stringResource(R.string.reminder_editor_save),
                    onClick = { onAction(ReminderEditorAction.Save) },
                    // A new entry is checked on tap instead, so the error can say what's missing.
                    enabled = !state.isSaving && (!state.isEditing || state.hasUnsavedChanges),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> EditorSkeleton(Modifier.padding(padding))
            state.isUnavailable -> EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.reminder_editor_missing),
                body = stringResource(R.string.reminders_empty_body),
                modifier = Modifier.padding(padding),
                action = {
                    OutlinedButton(onClick = { onAction(ReminderEditorAction.Close) }) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
            else -> EditorContent(state, onPick = { picking = it }, onAction = onAction, modifier = Modifier.padding(padding))
        }
    }

    when (picking) {
        EditorPicker.Time -> TimeOfDayPickerDialog(
            title = stringResource(R.string.reminder_editor_starts_at),
            selected = state.startTime,
            onPick = { time ->
                picking = null
                onAction(ReminderEditorAction.SetStartTime(time))
            },
            onDismiss = { picking = null },
        )
        EditorPicker.Routine -> OptionDialog(
            title = stringResource(R.string.reminder_editor_routine),
            body = stringResource(R.string.reminder_editor_routine_dialog_body),
            options = listOf(DialogOption<String?>(null, stringResource(R.string.reminder_editor_routine_none_option))) +
                state.routines.map { DialogOption<String?>(it.id, it.name) },
            selected = state.routine?.id,
            onSelect = { id ->
                picking = null
                onAction(ReminderEditorAction.SetRoutine(id))
            },
            onDismiss = { picking = null },
        )
        EditorPicker.Lead -> LeadTimeDialog(
            title = stringResource(R.string.reminder_editor_lead),
            body = stringResource(R.string.reminder_editor_lead_dialog_body),
            selected = state.leadMinutes,
            defaultMinutes = state.defaultLeadMinutes,
            onSelect = { minutes ->
                picking = null
                onAction(ReminderEditorAction.SetLead(minutes))
            },
            onDismiss = { picking = null },
        )
        null -> Unit
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            text = stringResource(if (state.isEditing) R.string.reminder_editor_discard_body_edit else R.string.reminder_editor_discard_body_create),
            confirmLabel = stringResource(R.string.editor_discard_confirm),
            dismissLabel = stringResource(R.string.editor_discard_dismiss),
            onConfirm = {
                confirmDiscard = false
                onAction(ReminderEditorAction.Close)
            },
            onDismiss = { confirmDiscard = false },
            destructive = true,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.reminder_editor_delete_title),
            text = stringResource(R.string.reminder_editor_delete_body),
            confirmLabel = stringResource(R.string.reminder_editor_delete_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmDelete = false
                onAction(ReminderEditorAction.Delete)
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

private enum class EditorPicker { Time, Routine, Lead }

@Composable
private fun EditorContent(
    state: ReminderEditorUiState,
    onPick: (EditorPicker) -> Unit,
    onAction: (ReminderEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val row = Modifier.padding(horizontal = Spacing.gutter)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Spacing.xs, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StartTimeBlock(time = state.startTime, onClick = { onPick(EditorPicker.Time) }, modifier = row)

        Text(
            stringResource(R.string.reminder_editor_days),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier = row.padding(top = Spacing.lg).semantics { heading() },
        )
        DayOfWeekPicker(
            selected = state.days,
            firstDayOfWeek = state.firstDayOfWeek,
            onToggle = { onAction(ReminderEditorAction.ToggleDay(it)) },
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        AnimatedVisibility(
            visible = state.showDaysError,
            enter = fadeIn(tween(ERROR_MILLIS)) + expandVertically(tween(ERROR_MILLIS)),
            exit = fadeOut(tween(ERROR_MILLIS)) + shrinkVertically(tween(ERROR_MILLIS)),
        ) {
            Text(
                stringResource(R.string.reminder_editor_days_error),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                modifier = row,
            )
        }

        SettingsRow(
            title = stringResource(R.string.reminder_editor_routine),
            supporting = stringResource(R.string.reminder_editor_routine_body),
            value = state.routine?.name ?: stringResource(R.string.reminder_editor_routine_none),
            onClick = { onPick(EditorPicker.Routine) },
            clickLabel = stringResource(R.string.reminder_editor_routine_click_label),
            modifier = row.padding(top = Spacing.lg),
        )
        val lead = state.leadMinutes
        SettingsRow(
            title = stringResource(R.string.reminder_editor_lead),
            value = if (lead != null) leadText(lead) else stringResource(R.string.lead_default, leadText(state.defaultLeadMinutes)),
            spokenValue = if (lead != null) leadSpoken(lead) else stringResource(R.string.lead_default, leadSpoken(state.defaultLeadMinutes)),
            onClick = { onPick(EditorPicker.Lead) },
            clickLabel = stringResource(R.string.reminder_editor_lead_click_label),
            modifier = row,
        )
    }
}

/** When the workout starts, as large as the screen allows: the one number a reminder is about. */
@Composable
private fun StartTimeBlock(time: LocalTime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.reminder_editor_starts_at)
    val text = timeOfDayText(time)
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.reminder_editor_time_click_label), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$label, $text" }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.displayMedium.tabularNumbers(), color = colors.onSurface, maxLines = 1)
    }
}

@Composable
private fun EditorSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(contentDescription = stringResource(R.string.reminder_editor_loading), modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonTimeHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.padding(top = Spacing.lg).fillMaxWidth(0.2f).height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(Spacing.xxl), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.padding(top = Spacing.lg).fillMaxWidth().height(SkeletonRowHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonRowHeight), MaterialTheme.shapes.large)
        }
    }
}

private val SkeletonTimeHeight = 104.dp
private val SkeletonRowHeight = 64.dp
private const val ERROR_MILLIS = 150

@ThemePreviews
@Composable
private fun ReminderEditorScreenPreview() {
    LiftBookTheme {
        ReminderEditorScreen(
            state = ReminderEditorUiState(
                isEditing = true,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                startTime = LocalTime.of(18, 30),
                routine = RoutineOption("push", "Push"),
                leadMinutes = 30,
                firstDayOfWeek = FirstDayOfWeek.MONDAY,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ReminderEditorScreenNewPreview() {
    LiftBookTheme {
        ReminderEditorScreen(
            state = ReminderEditorUiState(showDaysError = true, firstDayOfWeek = FirstDayOfWeek.SUNDAY),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ReminderEditorScreenLoadingPreview() {
    LiftBookTheme {
        ReminderEditorScreen(state = ReminderEditorUiState(isEditing = true, isLoading = true), snackbarHostState = remember { SnackbarHostState() }, onAction = {})
    }
}
