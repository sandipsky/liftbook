package com.example.liftbook.ui.feature.settings

import com.example.liftbook.MainViewModel
import com.example.liftbook.domain.model.FirstDayOfWeek
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.testing.FakeBackupRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository(UserPreferences(defaultRestSeconds = 120, firstDayOfWeek = FirstDayOfWeek.MONDAY))
    private val backups = FakeBackupRepository()
    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)
    private val events = mutableListOf<SettingsEvent>()

    private fun TestScope.settingsScreen(): SettingsViewModel =
        SettingsViewModel(settings, backups, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        }

    @Test
    fun `shows the settings as stored, and when the last backup was`() = runTest {
        backups.lastExportedAt.value = Instant.parse("2026-09-23T19:00:00Z")

        val state = settingsScreen().uiState.value

        assertFalse(state.isLoading)
        assertEquals(120, state.defaultRestSeconds)
        assertEquals(FirstDayOfWeek.MONDAY, state.firstDayOfWeek)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(Instant.parse("2026-09-23T19:00:00Z"), state.lastExportedAt)
    }

    @Test
    fun `each choice applies as it's picked`() = runTest {
        val viewModel = settingsScreen()

        viewModel.onAction(SettingsAction.SetDefaultRest(0))
        viewModel.onAction(SettingsAction.SetFirstDayOfWeek(FirstDayOfWeek.SUNDAY))
        viewModel.onAction(SettingsAction.SetTheme(ThemeMode.DARK))

        val state = viewModel.uiState.value
        assertEquals(0, state.defaultRestSeconds)
        assertEquals(FirstDayOfWeek.SUNDAY, state.firstDayOfWeek)
        assertEquals(ThemeMode.DARK, state.themeMode)
    }

    @Test
    fun `a choice that can't be saved says so, and stays as it was`() = runTest {
        val viewModel = settingsScreen()
        settings.failure = IOException("Disk full")

        viewModel.onAction(SettingsAction.SetTheme(ThemeMode.DARK))

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
        assertEquals(listOf<SettingsEvent>(SettingsEvent.SaveFailed), events)
    }

    @Test
    fun `the app follows the theme setting as it changes`() = runTest {
        val main = MainViewModel(settings)

        assertEquals(ThemeMode.SYSTEM, main.themeMode.value)

        settings.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, main.themeMode.value)
    }
}
