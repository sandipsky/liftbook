package com.example.liftbook.ui.feature.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.calculator.calendarWeeks
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.ui.components.dayOfMonthText
import com.example.liftbook.ui.components.monthText
import com.example.liftbook.ui.components.spokenDateText
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The month's name with arrows to the months either side. The calendar never goes past this month. */
@Composable
fun CalendarMonthHeader(
    calendar: CalendarMonth,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthText(calendar.month, today),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.history_previous_month))
        }
        IconButton(onClick = onNextMonth, enabled = calendar.hasNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.history_next_month))
        }
    }
}

/**
 * A month of training days (FR-4.4), in weeks that start on [firstDayOfWeek] (FR-6.2).
 * A day with a workout sits in a disc of the accent's soft tone, its number a weight heavier, so
 * it reads by shape and weight as well as colour; today wears a ring. Tapping a training day
 * opens what was done on it. Swiping across the grid turns the month, as the arrows do, and the
 * weeks slide the way the month went.
 */
@Composable
fun TrainingCalendarGrid(
    calendar: CalendarMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onOpenDay: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
        WeekdayRow(firstDayOfWeek = firstDayOfWeek, locale = locale)
        AnimatedContent(
            targetState = calendar,
            contentKey = { it.month },
            transitionSpec = {
                val direction = if (targetState.month > initialState.month) SlideDirection.Start else SlideDirection.End
                val slide = tween<IntOffset>(MONTH_SLIDE_MILLIS, easing = FastOutSlowInEasing)
                (fadeIn(tween(MONTH_SLIDE_MILLIS)) + slideIntoContainer(direction, slide) { it / SLIDE_OFFSET_DIVISOR }) togetherWith
                    (fadeOut(tween(MONTH_SLIDE_MILLIS)) + slideOutOfContainer(direction, slide) { it / SLIDE_OFFSET_DIVISOR })
            },
            label = "calendarMonth",
        ) { shown ->
            val weeks = remember(shown.month, firstDayOfWeek) { calendarWeeks(shown.month, firstDayOfWeek) }
            Column(Modifier.monthSwipe(canGoNext = shown.hasNext, onPreviousMonth = onPreviousMonth, onNextMonth = onNextMonth)) {
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                workouts = date?.let { shown.days[it] }.orEmpty(),
                                today = today,
                                onOpen = { date?.let(onOpenDay) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The weekdays' initials over the columns. Each day says its own weekday to TalkBack, so this is hidden. */
@Composable
private fun WeekdayRow(firstDayOfWeek: DayOfWeek, locale: Locale) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        repeat(DAYS_PER_WEEK) { offset ->
            Text(
                text = firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    workouts: List<WorkoutListItem>,
    today: LocalDate,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier.height(CellHeight))
        return
    }
    val colors = MaterialTheme.colorScheme
    val trained = workouts.isNotEmpty()
    val isToday = date == today
    val separator = stringResource(R.string.list_separator)
    val spokenDate = spokenDateText(date, today)
    val described = if (trained) {
        stringResource(R.string.history_day_spoken, spokenDate, workouts.joinToString(separator) { it.name })
    } else {
        spokenDate
    }
    val spoken = if (isToday) stringResource(R.string.history_today_spoken, described) else described
    // The whole cell is the target; the ripple keeps to the disc, however wide the column is.
    Box(
        modifier = modifier
            .height(CellHeight)
            .then(
                if (trained) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = DiscSize / 2),
                        onClickLabel = stringResource(R.string.history_workout_click_label),
                        role = Role.Button,
                        onClick = onOpen,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(DiscSize)
                .clip(CircleShape)
                .background(if (trained) colors.primaryContainer else Color.Transparent)
                .then(if (isToday) Modifier.border(TodayRing, if (trained) colors.primary else colors.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayOfMonthText(date),
                style = (if (trained) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium).tabularNumbers(),
                color = when {
                    trained -> colors.onPrimaryContainer
                    date.isAfter(today) -> colors.outline
                    else -> colors.onSurface
                },
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** A horizontal swipe turns the month: toward the start for the next, as a page turns. */
@Composable
private fun Modifier.monthSwipe(canGoNext: Boolean, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit): Modifier {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previous by rememberUpdatedState(onPreviousMonth)
    val next by rememberUpdatedState(onNextMonth)
    val nextAllowed by rememberUpdatedState(canGoNext)
    return pointerInput(isRtl) {
        val threshold = SwipeThreshold.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                // Dragging toward the start edge brings the next month in.
                val towardStart = if (isRtl) total > threshold else total < -threshold
                val towardEnd = if (isRtl) total < -threshold else total > threshold
                when {
                    towardStart && nextAllowed -> next()
                    towardEnd -> previous()
                }
            },
        ) { change, dragAmount ->
            change.consume()
            total += dragAmount
        }
    }
}

private const val DAYS_PER_WEEK = 7
private const val MONTH_SLIDE_MILLIS = 220
private const val SLIDE_OFFSET_DIVISOR = 8
private val CellHeight = 48.dp
private val DiscSize = 40.dp
private val TodayRing = 1.dp
private val SwipeThreshold = 48.dp

@ThemePreviews
@Composable
private fun TrainingCalendarPreview() {
    LiftBookPreview {
        Column(Modifier.padding(vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val calendar = HistoryPreviewData.calendar()
            CalendarMonthHeader(calendar = calendar, today = HistoryPreviewData.today, onPreviousMonth = {}, onNextMonth = {})
            TrainingCalendarGrid(
                calendar = calendar,
                today = HistoryPreviewData.today,
                firstDayOfWeek = DayOfWeek.MONDAY,
                onOpenDay = {},
                onPreviousMonth = {},
                onNextMonth = {},
            )
        }
    }
}
