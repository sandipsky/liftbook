package com.example.liftbook.ui.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.RestDurationDialog
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SettingChoice
import com.example.liftbook.ui.components.SettingsRow
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.restDurationSpoken
import com.example.liftbook.ui.components.restDurationText
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SettingsRoute(
    onNavigateUp: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenDataManagement: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.SaveFailed -> resources.getString(R.string.settings_save_failed)
                SettingsEvent.DataDeleted -> resources.getString(R.string.data_cleared)
                SettingsEvent.DeleteFailed -> resources.getString(R.string.data_clear_failed)
                SettingsEvent.CloudUnreachable -> resources.getString(R.string.data_clear_cloud_unreachable)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    SettingsScreen(
        state = state,
        clearConfirmation = viewModel.clearConfirmation,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                SettingsAction.NavigateUp -> onNavigateUp()
                SettingsAction.OpenReminders -> onOpenReminders()
                SettingsAction.OpenDataManagement -> onOpenDataManagement()
                SettingsAction.OpenAccount -> onOpenAccount()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Settings (FR-6.2). Few enough to see at once, so every choice is in view rather than behind a
 * dialog of its own; only the rest, with its long list of durations, opens one. Each applies as
 * it's picked. Reminders (FR-7) and backups have their own screens, one step further: one is a
 * schedule to edit, and what the other does can't be undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    clearConfirmation: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (SettingsAction) -> Unit,
) {
    var choosingRest by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.settings_title),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(SettingsAction.NavigateUp) },
                showTitle = headlineScrolledAway,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = state.isLoading,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "settingsContent",
        ) { loading ->
            if (loading) {
                SettingsSkeleton(Modifier.padding(padding))
            } else {
                SettingsList(state, listState, padding, onChooseRest = { choosingRest = true }, onAction = onAction)
            }
        }
    }

    if (state.isConfirmingDelete) {
        ClearDataDialog(
            confirmation = clearConfirmation,
            lastExportedAt = state.lastExportedAt,
            accountEmail = state.accountEmail,
            today = state.today,
            zone = state.zone,
            onConfirm = { onAction(SettingsAction.ConfirmDeleteAllData) },
            onDismiss = { onAction(SettingsAction.DismissDeleteAllData) },
        )
    }

    if (choosingRest) {
        RestDurationDialog(
            title = stringResource(R.string.rest_dialog_title_default),
            body = stringResource(R.string.rest_dialog_body_default),
            selected = state.defaultRestSeconds,
            onSelect = { seconds ->
                choosingRest = false
                if (seconds != null) onAction(SettingsAction.SetDefaultRest(seconds))
            },
            onDismiss = { choosingRest = false },
        )
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState,
    listState: LazyListState,
    padding: PaddingValues,
    onChooseRest: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val row = Modifier.padding(horizontal = Spacing.gutter)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(key = "header", contentType = "header") {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = Spacing.xs, bottom = Spacing.md)
                    .semantics { heading() },
            )
        }

        item(key = "accountHeader", contentType = "sectionHeader") {
            SectionHeader(title = stringResource(R.string.settings_section_account), modifier = Modifier.padding(bottom = Spacing.xxs))
        }
        item(key = "account", contentType = "row") {
            SettingsRow(
                title = stringResource(R.string.account_title),
                supporting = when {
                    !state.accountsAvailable -> stringResource(R.string.settings_account_unavailable)
                    state.accountEmail != null -> state.accountEmail
                    else -> stringResource(R.string.settings_account_signed_out)
                },
                showsChevron = true,
                onClick = { onAction(SettingsAction.OpenAccount) },
                clickLabel = stringResource(R.string.settings_account_click_label),
                modifier = row,
            )
        }

        item(key = "trainingHeader", contentType = "sectionHeader") {
            SectionHeader(
                title = stringResource(R.string.settings_section_training),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
            )
        }
        item(key = "rest", contentType = "row") {
            SettingsRow(
                title = stringResource(R.string.settings_rest_title),
                supporting = stringResource(R.string.settings_rest_body),
                value = restDurationText(state.defaultRestSeconds),
                spokenValue = restDurationSpoken(state.defaultRestSeconds),
                onClick = onChooseRest,
                clickLabel = stringResource(R.string.settings_rest_click_label),
                modifier = row,
            )
        }
        item(key = "weekStart", contentType = "choice") {
            SettingChoice(
                title = stringResource(R.string.settings_week_start_title),
                supporting = stringResource(R.string.settings_week_start_body),
                options = FirstDayOfWeek.entries,
                selected = state.firstDayOfWeek,
                onSelect = { onAction(SettingsAction.SetFirstDayOfWeek(it)) },
                label = { it.label() },
                spokenLabel = { stringResource(R.string.settings_week_start_spoken, it.label()) },
                modifier = row,
            )
        }

        item(key = "remindersHeader", contentType = "sectionHeader") {
            SectionHeader(
                title = stringResource(R.string.settings_section_reminders),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
            )
        }
        item(key = "reminders", contentType = "row") {
            SettingsRow(
                title = stringResource(R.string.settings_reminders_title),
                supporting = when {
                    !state.remindersEnabled -> stringResource(R.string.settings_reminders_off)
                    state.remindersPerWeek == 0 -> stringResource(R.string.settings_reminders_none)
                    else -> pluralStringResource(R.plurals.settings_reminders_per_week, state.remindersPerWeek, state.remindersPerWeek)
                },
                showsChevron = true,
                onClick = { onAction(SettingsAction.OpenReminders) },
                clickLabel = stringResource(R.string.settings_reminders_click_label),
                modifier = row,
            )
        }

        item(key = "appearanceHeader", contentType = "sectionHeader") {
            SectionHeader(
                title = stringResource(R.string.settings_section_appearance),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
            )
        }
        item(key = "theme", contentType = "choice") {
            SettingChoice(
                title = stringResource(R.string.settings_theme_title),
                options = ThemeMode.entries,
                selected = state.themeMode,
                onSelect = { onAction(SettingsAction.SetTheme(it)) },
                label = { stringResource(it.labelRes()) },
                spokenLabel = { stringResource(it.spokenRes()) },
                modifier = row,
            )
        }

        item(key = "dataHeader", contentType = "sectionHeader") {
            SectionHeader(
                title = stringResource(R.string.settings_section_data),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
            )
        }
        item(key = "backup", contentType = "row") {
            val lastBackup = state.lastExportedAt
            SettingsRow(
                title = stringResource(R.string.data_title),
                supporting = if (lastBackup != null) {
                    stringResource(R.string.settings_backup_last, backupDayText(lastBackup, state.today, state.zone))
                } else {
                    stringResource(R.string.settings_backup_never)
                },
                showsChevron = true,
                onClick = { onAction(SettingsAction.OpenDataManagement) },
                clickLabel = stringResource(R.string.settings_backup_click_label),
                modifier = row,
            )
        }
        item(key = "delete", contentType = "row") {
            // Destructive, so in the error colour and always with its icon; the dialog asks for a typed word.
            SettingsRow(
                title = stringResource(R.string.data_clear_title),
                supporting = stringResource(R.string.data_clear_body),
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = { onAction(SettingsAction.DeleteAllData) },
                clickLabel = stringResource(R.string.data_clear_click_label),
                enabled = !state.isDeleting,
                modifier = row,
            )
        }
    }
}

@Composable
private fun SettingsSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(contentDescription = stringResource(R.string.settings_loading), modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SkeletonBlock(Modifier.padding(bottom = Spacing.md).fillMaxWidth(0.4f).height(Spacing.xl))
            SkeletonBlock(Modifier.padding(vertical = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonRowHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonChoiceHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonRowHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonChoiceHeight), MaterialTheme.shapes.large)
        }
    }
}

private val SkeletonRowHeight = 64.dp
private val SkeletonChoiceHeight = 112.dp
private const val CONTENT_FADE_MILLIS = 200

private val previewToday = LocalDate.of(2026, 9, 26)

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    LiftBookTheme {
        SettingsScreen(
            state = SettingsUiState(
                isLoading = false,
                defaultRestSeconds = 120,
                firstDayOfWeek = FirstDayOfWeek.MONDAY,
                themeMode = ThemeMode.SYSTEM,
                remindersPerWeek = 4,
                lastExportedAt = Instant.parse("2026-09-23T19:00:00Z"),
                accountEmail = "sam@example.com",
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
private fun SettingsScreenLoadingPreview() {
    LiftBookTheme {
        SettingsScreen(
            state = SettingsUiState(today = previewToday),
            clearConfirmation = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
