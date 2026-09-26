package com.example.liftbook.ui.feature.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.ReminderTimes
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.notification.channel.NotificationChannels
import com.example.liftbook.ui.components.AlertIssue
import com.example.liftbook.ui.components.BottomActionBar
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SettingChoice
import com.example.liftbook.ui.components.SettingsRow
import com.example.liftbook.ui.components.SettingsSwitchRow
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.components.openAlertSettings
import com.example.liftbook.ui.components.rememberAlertIssue
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs

@Composable
fun ReminderListRoute(
    onNavigateUp: () -> Unit,
    onOpenSchedule: (scheduleId: String) -> Unit,
    onAddSchedule: () -> Unit,
    viewModel: ReminderListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val alerts = rememberAlertIssue(NotificationChannels.WORKOUT_REMINDERS)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ReminderListEvent.SaveFailed -> snackbarHostState.showSnackbar(resources.getString(R.string.settings_save_failed))
            }
        }
    }

    // Notifications are asked for once something will remind: the moment they're for (FR-7.6).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { alerts.recheck() }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.remindsAnything) {
        if (state.remindsAnything && !askedForNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ReminderListScreen(
        state = state,
        alertIssue = alerts.issue.takeIf { state.remindsAnything },
        snackbarHostState = snackbarHostState,
        onFixAlerts = { issue -> openAlertSettings(context, issue) },
        onAction = { action ->
            when (action) {
                ReminderListAction.NavigateUp -> onNavigateUp()
                ReminderListAction.AddSchedule -> onAddSchedule()
                is ReminderListAction.OpenSchedule -> onOpenSchedule(action.scheduleId)
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Workout reminders (FR-7): the global switch and what's next first, then the week's schedule —
 * each entry with its own switch — then the defaults every entry starts from. Adding a workout is
 * the screen's one primary action, at the bottom within thumb reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    state: ReminderListUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (ReminderListAction) -> Unit,
    alertIssue: AlertIssue? = null,
    onFixAlerts: (AlertIssue) -> Unit = {},
) {
    var choosingLead by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.reminders_title),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(ReminderListAction.NavigateUp) },
                showTitle = headlineScrolledAway,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (!state.isLoading && state.schedules.isNotEmpty()) {
                BottomActionBar(text = stringResource(R.string.reminders_add), onClick = { onAction(ReminderListAction.AddSchedule) })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = state.isLoading,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "remindersContent",
        ) { loading ->
            if (loading) {
                ReminderListSkeleton(Modifier.padding(padding))
            } else {
                ReminderList(state, alertIssue, listState, padding, onFixAlerts, onChooseLead = { choosingLead = true }, onAction = onAction)
            }
        }
    }

    if (choosingLead) {
        LeadTimeDialog(
            title = stringResource(R.string.reminders_lead_title),
            body = stringResource(R.string.reminders_lead_body),
            selected = state.defaultLeadMinutes,
            onSelect = { minutes ->
                choosingLead = false
                if (minutes != null) onAction(ReminderListAction.SetDefaultLead(minutes))
            },
            onDismiss = { choosingLead = false },
        )
    }
}

@Composable
private fun ReminderList(
    state: ReminderListUiState,
    alertIssue: AlertIssue?,
    listState: LazyListState,
    padding: PaddingValues,
    onFixAlerts: (AlertIssue) -> Unit,
    onChooseLead: () -> Unit,
    onAction: (ReminderListAction) -> Unit,
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
                text = stringResource(R.string.reminders_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = Spacing.xs, bottom = Spacing.md)
                    .semantics { heading() },
            )
        }
        item(key = "master", contentType = "switch") {
            SettingsSwitchRow(
                title = stringResource(R.string.reminders_master_title),
                supporting = masterSupportingText(state),
                checked = state.remindersEnabled,
                onCheckedChange = { onAction(ReminderListAction.SetRemindersEnabled(it)) },
                modifier = row,
            )
        }
        if (alertIssue != null) {
            item(key = "alert", contentType = "alert") {
                ReminderAlertNotice(issue = alertIssue, onFix = { onFixAlerts(alertIssue) }, modifier = row.animateItem())
            }
        }

        item(key = "scheduleHeader", contentType = "sectionHeader") {
            SectionHeader(
                title = stringResource(R.string.reminders_section_schedule),
                trailing = state.perWeek.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.reminders_per_week, it, it) },
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
            )
        }
        if (state.schedules.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = Icons.Outlined.EventRepeat,
                    title = stringResource(R.string.reminders_empty_title),
                    body = stringResource(R.string.reminders_empty_body),
                    modifier = Modifier.padding(top = Spacing.xs),
                    action = {
                        Button(onClick = { onAction(ReminderListAction.AddSchedule) }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(IconSize.inline))
                            Spacer(Modifier.size(Spacing.xs))
                            Text(stringResource(R.string.reminders_add))
                        }
                    },
                )
            }
        }
        items(state.schedules, key = { it.schedule.id }, contentType = { "schedule" }) { item ->
            ScheduleCard(
                item = item,
                firstDayOfWeek = state.firstDayOfWeek,
                defaultLeadMinutes = state.defaultLeadMinutes,
                onOpen = { onAction(ReminderListAction.OpenSchedule(item.schedule.id)) },
                onToggle = { onAction(ReminderListAction.SetScheduleEnabled(item.schedule.id, it)) },
                modifier = row.animateItem(),
            )
        }

        item(key = "defaultsHeader", contentType = "sectionHeader") {
            SectionHeader(
                title = stringResource(R.string.reminders_section_defaults),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
            )
        }
        item(key = "lead", contentType = "row") {
            SettingsRow(
                title = stringResource(R.string.reminders_lead_title),
                supporting = stringResource(R.string.reminders_lead_body),
                value = leadText(state.defaultLeadMinutes),
                spokenValue = leadSpoken(state.defaultLeadMinutes),
                onClick = onChooseLead,
                clickLabel = stringResource(R.string.reminders_lead_click_label),
                modifier = row,
            )
        }
        item(key = "snooze", contentType = "choice") {
            SettingChoice(
                title = stringResource(R.string.reminders_snooze_title),
                supporting = stringResource(R.string.reminders_snooze_body),
                options = ReminderTimes.SNOOZE_OPTIONS,
                // A snooze length set some other way, as by an import, still shows as its nearest.
                selected = ReminderTimes.SNOOZE_OPTIONS.minBy { abs(it - state.snoozeMinutes) },
                onSelect = { onAction(ReminderListAction.SetSnooze(it)) },
                label = { stringResource(R.string.reminders_snooze_option, it) },
                spokenLabel = { pluralStringResource(R.plurals.reminders_snooze_spoken, it, it) },
                modifier = row,
            )
        }
    }
}

/** Under the global switch: what it's doing now — off, nothing on yet, or the next workout. */
@Composable
private fun masterSupportingText(state: ReminderListUiState): String {
    val next = state.next
    return when {
        !state.remindersEnabled -> stringResource(R.string.reminders_master_off)
        next == null && state.schedules.isNotEmpty() -> stringResource(R.string.reminders_master_all_off)
        next == null -> stringResource(R.string.reminders_master_idle)
        else -> {
            val start = next.startsAt.atZone(state.zone)
            val day = nearDayText(start.toLocalDate(), state.today)
            val time = timeOfDayText(start.toLocalTime())
            if (next.routineName != null) {
                stringResource(R.string.reminders_next_routine, day, time, next.routineName)
            } else {
                stringResource(R.string.reminders_next, day, time)
            }
        }
    }
}

@Composable
private fun ReminderListSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(contentDescription = stringResource(R.string.reminders_loading), modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SkeletonBlock(Modifier.padding(bottom = Spacing.md).fillMaxWidth(0.45f).height(Spacing.xl))
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonRowHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            repeat(2) {
                SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonCardHeight), MaterialTheme.shapes.large)
            }
        }
    }
}

private val SkeletonRowHeight = 64.dp
private val SkeletonCardHeight = 88.dp
private const val CONTENT_FADE_MILLIS = 200

private val previewToday = LocalDate.of(2026, 9, 26)

private val previewSchedules = listOf(
    ScheduleItem(
        WorkoutSchedule(
            id = "a",
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            startTime = LocalTime.of(7, 0),
            routineName = "Push",
        ),
    ),
    ScheduleItem(
        WorkoutSchedule(id = "b", days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), startTime = LocalTime.of(18, 30), routineName = "Pull", leadMinutes = 30),
    ),
    ScheduleItem(
        WorkoutSchedule(id = "c", days = setOf(DayOfWeek.SATURDAY), startTime = LocalTime.of(9, 0), isEnabled = false),
    ),
)

@ThemePreviews
@Composable
private fun ReminderListScreenPreview() {
    LiftBookTheme {
        ReminderListScreen(
            state = ReminderListUiState(
                isLoading = false,
                schedules = previewSchedules,
                next = NextWorkout(previewToday.plusDays(2).atTime(7, 0).toInstant(ZoneOffset.UTC), "Push"),
                perWeek = 5,
                firstDayOfWeek = FirstDayOfWeek.MONDAY,
                today = previewToday,
                zone = ZoneOffset.UTC,
            ),
            alertIssue = AlertIssue.AlarmsOff,
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ReminderListScreenEmptyPreview() {
    LiftBookTheme {
        ReminderListScreen(
            state = ReminderListUiState(isLoading = false, today = previewToday, zone = ZoneOffset.UTC),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun ReminderListScreenLoadingPreview() {
    LiftBookTheme {
        ReminderListScreen(
            state = ReminderListUiState(today = previewToday),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
