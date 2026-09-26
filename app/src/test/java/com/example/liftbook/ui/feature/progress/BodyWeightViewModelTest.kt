package com.example.liftbook.ui.feature.progress

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.liftbook.core.unit.lbToKg
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.model.ProgressRange
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.testing.FakeBodyWeightRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 9, 26)
    private val clock = Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()

    // The field's text is formatted in the default locale; pin it so "176.37" reads the same everywhere.
    private val defaultLocale = Locale.getDefault()

    @Before
    fun pinLocale() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun entry(id: String, date: LocalDate, kg: Double) = BodyWeightEntry(id, date, kg)

    private fun TestScope.bodyWeight(repository: FakeBodyWeightRepository): BodyWeightViewModel =
        BodyWeightViewModel(repository, settings, clock, SavedStateHandle()).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    @Test
    fun `shows the range's weigh-ins with a trend smoothed over the days before each`() = runTest {
        val repository = FakeBodyWeightRepository(
            listOf(
                entry("old", LocalDate.of(2026, 5, 1), 90.0),
                // Just before the range: still in the first point's week, so the trend counts it.
                entry("before", LocalDate.of(2026, 6, 24), 84.0),
                entry("a", LocalDate.of(2026, 6, 27), 82.0),
                entry("b", LocalDate.of(2026, 9, 25), 80.0),
            ),
        )

        val state = bodyWeight(repository).uiState.value

        assertEquals(ProgressRange.THREE_MONTHS, state.range)
        assertEquals(listOf("a", "b"), state.entries.map { it.id })
        assertEquals(83.0, state.trend.first().weightKg, 1e-9)
        assertEquals(80.0 - 83.0, state.change!!, 1e-9)
        assertTrue(state.hasEntries)
    }

    @Test
    fun `logging opens on today with the last weight ready to replace, and saves it`() = runTest {
        val repository = FakeBodyWeightRepository(listOf(entry("y", today.minusDays(1), 82.4)))
        val viewModel = bodyWeight(repository)

        viewModel.onAction(BodyWeightAction.Log)
        val log = viewModel.uiState.value.log!!
        assertEquals(today, log.date)
        assertNull(log.existing)
        assertEquals("82.4", viewModel.logWeight.text.toString())
        assertEquals(0 until 4, viewModel.logWeight.selection.let { it.start until it.end })

        viewModel.logWeight.setTextAndPlaceCursorAtEnd("81.9")
        viewModel.onAction(BodyWeightAction.SaveLog)

        assertNull(viewModel.uiState.value.log)
        assertEquals(81.9, repository.entries.value.first { it.date == today }.weightKg, 1e-9)
    }

    @Test
    fun `a weight saved untouched in pounds is kept exactly as stored`() = runTest {
        settings.userPreferences.value = UserPreferences(weightUnit = WeightUnit.LB)
        val repository = FakeBodyWeightRepository(listOf(entry("t", today, 80.0)))
        val viewModel = bodyWeight(repository)

        viewModel.onAction(BodyWeightAction.Edit(repository.entries.value.single()))
        assertEquals("176.37", viewModel.logWeight.text.toString())
        viewModel.onAction(BodyWeightAction.SaveLog)

        assertEquals(80.0, repository.entries.value.single().weightKg, 0.0)
    }

    @Test
    fun `a weight typed in pounds is stored in kilograms`() = runTest {
        settings.userPreferences.value = UserPreferences(weightUnit = WeightUnit.LB)
        val repository = FakeBodyWeightRepository()
        val viewModel = bodyWeight(repository)

        viewModel.onAction(BodyWeightAction.Log)
        viewModel.logWeight.setTextAndPlaceCursorAtEnd("180")
        viewModel.onAction(BodyWeightAction.SaveLog)

        assertEquals(lbToKg(180.0), repository.entries.value.single().weightKg, 1e-9)
    }

    @Test
    fun `saving without a weight says so, and saves nothing`() = runTest {
        val repository = FakeBodyWeightRepository()
        val viewModel = bodyWeight(repository)

        viewModel.onAction(BodyWeightAction.Log)
        viewModel.onAction(BodyWeightAction.SaveLog)

        assertTrue(viewModel.uiState.value.log!!.showError)
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `choosing a day that has a weigh-in shows it, to replace`() = runTest {
        val yesterday = today.minusDays(1)
        val repository = FakeBodyWeightRepository(listOf(entry("y", yesterday, 83.0), entry("t", today, 82.0)))
        val viewModel = bodyWeight(repository)

        viewModel.onAction(BodyWeightAction.Log)
        viewModel.onAction(BodyWeightAction.ChangeLogDate(yesterday))

        assertEquals("y", viewModel.uiState.value.log!!.existing?.id)
        assertEquals("83", viewModel.logWeight.text.toString())
        // Days to come can't be logged.
        viewModel.onAction(BodyWeightAction.ChangeLogDate(today.plusDays(1)))
        assertEquals(yesterday, viewModel.uiState.value.log!!.date)
    }

    @Test
    fun `deleting a weigh-in offers it back, and Undo restores it`() = runTest {
        val repository = FakeBodyWeightRepository(listOf(entry("t", today, 82.0)))
        val viewModel = bodyWeight(repository)

        viewModel.events.test {
            viewModel.onAction(BodyWeightAction.Edit(repository.entries.value.single()))
            assertTrue(viewModel.uiState.value.log!!.isEditing)
            viewModel.onAction(BodyWeightAction.DeleteLogged)
            val deleted = awaitItem() as BodyWeightLogEvent.Deleted
            assertTrue(repository.entries.value.isEmpty())

            viewModel.onAction(BodyWeightAction.Restore(deleted.entry))
        }
        assertEquals(82.0, repository.entries.value.single { it.date == today }.weightKg, 0.0)
    }

    @Test
    fun `a failed save keeps the sheet open and says so`() = runTest {
        val repository = FakeBodyWeightRepository().apply { failNextWrite = true }
        val viewModel = bodyWeight(repository)

        viewModel.events.test {
            viewModel.onAction(BodyWeightAction.Log)
            viewModel.logWeight.setTextAndPlaceCursorAtEnd("80")
            viewModel.onAction(BodyWeightAction.SaveLog)
            assertEquals(BodyWeightLogEvent.SaveFailed, awaitItem())
        }
        assertFalse(viewModel.uiState.value.log!!.isSaving)
    }
}
