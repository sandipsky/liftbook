package com.example.liftbook.ui.feature.reminders

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.ReminderTimes
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.WorkoutSchedule
import com.example.liftbook.ui.components.AlertIssue
import com.example.liftbook.ui.components.DialogOption
import com.example.liftbook.ui.components.OptionDialog
import com.example.liftbook.ui.components.timeOfDayText
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * One entry in the schedule: the time it starts, strongest, then its days and what it's for.
 * The row opens the editor; the switch turns just this entry on or off (FR-7.7). An entry
 * that's off keeps its place but steps back, as an alarm clock's does.
 */
@Composable
fun ScheduleCard(
    item: ScheduleItem,
    firstDayOfWeek: FirstDayOfWeek,
    defaultLeadMinutes: Int,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val schedule = item.schedule
    val timeColor by animateColorAsState(
        targetValue = if (schedule.isEnabled) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = tween(TOGGLE_MILLIS),
        label = "scheduleTime",
    )
    val time = timeOfDayText(schedule.startTime)
    val days = daysText(schedule.days, firstDayOfWeek)
    val spokenDays = daysSpoken(schedule.days, firstDayOfWeek)
    val ownLead = schedule.leadMinutes?.takeIf { it != defaultLeadMinutes }
    val skipped = if (item.isSkippedToday) stringResource(R.string.reminders_skipped_today) else null
    val meta = listOfNotNull(schedule.routineName, ownLead?.let { leadText(it) }, skipped)
        .joinToString(stringResource(R.string.list_separator))
    // Heard in words, not as the separators and short day names the eye reads.
    val spoken = listOfNotNull(time, spokenDays, schedule.routineName, ownLead?.let { leadSpoken(it) }, skipped)
        .joinToString(stringResource(R.string.list_separator_spoken))
    val switchLabel = stringResource(R.string.reminders_entry_switch, time, spokenDays)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.reminders_entry_click_label), onClick = onOpen)
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken }) {
            Text(time, style = MaterialTheme.typography.headlineSmall.tabularNumbers(), color = timeColor, maxLines = 1)
            Text(
                days,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = schedule.isEnabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = switchLabel },
        )
    }
}

/**
 * What stands between the reminders and the user, with the setting that fixes it: said in
 * words and with its icon, so it never rests on colour. A tonal step up from the rows, with
 * only the icon in the error colour — it's a warning, not a failure, and mustn't outshout the
 * screen's own action.
 */
@Composable
fun ReminderAlertNotice(issue: AlertIssue, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHigh)
            .padding(start = Spacing.md, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            when (issue) {
                AlertIssue.NotificationsOff -> Icons.Outlined.NotificationsOff
                AlertIssue.AlarmsOff -> Icons.Outlined.AlarmOff
            },
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(IconSize.inline),
        )
        Text(
            text = stringResource(
                when (issue) {
                    AlertIssue.NotificationsOff -> R.string.reminders_notifications_off
                    AlertIssue.AlarmsOff -> R.string.reminders_alarms_off
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
        )
        TextButton(onClick = onFix, modifier = Modifier.heightIn(min = TouchTarget)) {
            Text(stringResource(R.string.rest_alerts_fix), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The week as seven round toggles, in the order it runs from the user's first day. Picked days
 * are in ink, as chips are. Each is a full-height target a thumb can't miss, and TalkBack hears
 * the day's full name with whether it's picked.
 */
@Composable
fun DayOfWeekPicker(
    selected: Set<DayOfWeek>,
    firstDayOfWeek: FirstDayOfWeek,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier.fillMaxWidth()) {
        firstDayOfWeek.week().forEach { day ->
            val isSelected = day in selected
            val container by animateColorAsState(
                targetValue = if (isSelected) colors.inverseSurface else colors.surfaceContainerHigh,
                animationSpec = tween(TOGGLE_MILLIS),
                label = "dayContainer",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) colors.inverseOnSurface else colors.onSurface,
                animationSpec = tween(TOGGLE_MILLIS),
                label = "dayContent",
            )
            val fullName = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            Box(
                Modifier
                    .weight(1f)
                    .height(TouchTarget)
                    .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onToggle(day) })
                    .semantics { contentDescription = fullName },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(DayDisc)
                        .clip(CircleShape)
                        .background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                        style = MaterialTheme.typography.titleSmall,
                        color = content,
                        // The toggle says the whole name; "M" alone says nothing.
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

/**
 * Picks when to remind (FR-7.2). For an entry, the first choice follows the default, which it
 * names; for the default itself there's no such choice.
 */
@Composable
fun LeadTimeDialog(
    title: String,
    body: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
    /** Offered first, for an entry; null when picking the default itself. */
    defaultMinutes: Int? = null,
) {
    val defaultOption = defaultMinutes?.let {
        DialogOption<Int?>(
            value = null,
            label = stringResource(R.string.lead_default, leadText(it)),
            spoken = stringResource(R.string.lead_default, leadSpoken(it)),
        )
    }
    val options = listOfNotNull(defaultOption) + ReminderTimes.LEAD_OPTIONS.map { minutes ->
        DialogOption<Int?>(minutes, label = leadText(minutes), spoken = leadSpoken(minutes))
    }
    OptionDialog(title = title, body = body, options = options, selected = selected, onSelect = onSelect, onDismiss = onDismiss)
}

private val TouchTarget = 48.dp
private val DayDisc = 40.dp
private const val TOGGLE_MILLIS = 150

@ThemePreviews
@Composable
private fun ReminderComponentsPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ScheduleCard(
                item = ScheduleItem(
                    WorkoutSchedule(
                        id = "a",
                        days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        startTime = LocalTime.of(18, 0),
                        routineName = "Push",
                        leadMinutes = 30,
                    ),
                ),
                firstDayOfWeek = FirstDayOfWeek.MONDAY,
                defaultLeadMinutes = 10,
                onOpen = {},
                onToggle = {},
            )
            ScheduleCard(
                item = ScheduleItem(
                    WorkoutSchedule(id = "b", days = setOf(DayOfWeek.SATURDAY), startTime = LocalTime.of(7, 30), isEnabled = false),
                ),
                firstDayOfWeek = FirstDayOfWeek.MONDAY,
                defaultLeadMinutes = 10,
                onOpen = {},
                onToggle = {},
            )
            ReminderAlertNotice(issue = AlertIssue.AlarmsOff, onFix = {})
            DayOfWeekPicker(
                selected = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                firstDayOfWeek = FirstDayOfWeek.MONDAY,
                onToggle = {},
            )
        }
    }
}
