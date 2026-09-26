package com.example.liftbook.ui.feature.history

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.liftbook.domain.calculator.byDay
import com.example.liftbook.domain.calculator.totals
import com.example.liftbook.ui.theme.LiftBookTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/** The history screen, driven through its semantics as TalkBack or a thumb would. */
@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<HistoryAction>()

    private fun show(state: HistoryUiState, items: List<HistoryListItem> = emptyList()) {
        compose.setContent {
            LiftBookTheme {
                HistoryScreen(
                    state = state,
                    workouts = remember { MutableStateFlow(PagingData.from(items)) }.collectAsLazyPagingItems(),
                    onAction = { actions += it },
                )
            }
        }
    }

    private fun state(view: HistoryView = HistoryView.List, calendar: CalendarMonth? = null) =
        HistoryUiState(view = view, calendar = calendar, today = HistoryPreviewData.today, zone = HistoryPreviewData.zone)

    @Test
    fun `the list shows each workout under its month, and opens it`() {
        show(state(), HistoryPreviewData.listItems)

        compose.onNodeWithText("September").assertIsDisplayed()
        compose.onNodeWithText("Evening workout").assertIsDisplayed()
        // Nothing was lifted in it, so it shows the sets done instead of 0 kg.
        compose.onNodeWithText("9").assertIsDisplayed()
        compose.onNodeWithText("Evening workout").performClick()

        assertEquals(listOf(HistoryAction.OpenWorkout("w8")), actions)
    }

    @Test
    fun `an empty history says what goes there, and how to fill it`() {
        show(state())

        compose.onNodeWithText("No workouts yet").assertIsDisplayed()
        compose.onNodeWithText("Start a workout").performClick()

        assertEquals(listOf(HistoryAction.StartWorkout), actions)
    }

    @Test
    fun `the top bar switches to the calendar`() {
        show(state(), HistoryPreviewData.listItems)

        compose.onNodeWithContentDescription("Show calendar").performClick()

        assertEquals(listOf(HistoryAction.ShowView(HistoryView.Calendar)), actions)
    }

    @Test
    fun `tapping a training day opens its workout`() {
        show(state(HistoryView.Calendar, HistoryPreviewData.calendar()))

        compose.onNodeWithText("September").assertIsDisplayed()
        compose.onNodeWithContentDescription("Evening workout", substring = true).performClick()

        assertEquals(listOf(HistoryAction.OpenWorkout("w8")), actions)
    }

    @Test
    fun `a day with two workouts asks which`() {
        val september = YearMonth.of(2026, 9)
        val twoInADay = HistoryPreviewData.workouts.take(2).mapIndexed { index, workout ->
            // Both on the 24th: the morning and the evening.
            val start = september.atDay(24).atTime(7 + index * 11, 0).atZone(HistoryPreviewData.zone).toInstant()
            workout.copy(startedAt = start, finishedAt = start.plusSeconds(3_600))
        }
        val calendar = CalendarMonth(september, twoInADay.byDay(HistoryPreviewData.zone), twoInADay.totals(), hasNext = false)
        show(state(HistoryView.Calendar, calendar))

        compose.onNodeWithContentDescription("Push", substring = true).performClick()
        compose.onNodeWithText("Evening workout").assertIsDisplayed().performClick()

        assertEquals(listOf(HistoryAction.OpenWorkout("w8")), actions)
    }

    @Test
    fun `the calendar turns back a month, not forward past this one`() {
        show(state(HistoryView.Calendar, HistoryPreviewData.calendar()))

        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("Previous month").performClick()

        assertEquals(listOf(HistoryAction.PreviousMonth), actions)
    }
}
