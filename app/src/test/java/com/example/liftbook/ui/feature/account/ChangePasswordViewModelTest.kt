package com.example.liftbook.ui.feature.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.testing.FakeAccountRepository
import com.example.liftbook.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accounts = FakeAccountRepository(FakeAccountRepository.account()).apply { passwords["sam@example.com"] = "hunter22" }
    private val events = mutableListOf<ChangePasswordEvent>()

    private fun TestScope.changePassword(): ChangePasswordViewModel = ChangePasswordViewModel(accounts).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
    }

    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `the new password must be typed twice, and be long enough`() = runTest {
        val viewModel = changePassword()
        viewModel.newState.type("abc")
        viewModel.confirmState.type("abd")

        viewModel.onAction(ChangePasswordAction.Change)

        val state = viewModel.uiState.value
        assertEquals(FieldError.BLANK, state.currentError)
        assertEquals(FieldError.TOO_SHORT, state.newError)
        assertEquals(FieldError.MISMATCH, state.confirmError)
        assertEquals("hunter22", accounts.passwords["sam@example.com"])
    }

    @Test
    fun `the wrong current password is reported`() = runTest {
        val viewModel = changePassword()
        viewModel.currentState.type("wrong")
        viewModel.newState.type("newpass1")
        viewModel.confirmState.type("newpass1")

        viewModel.onAction(ChangePasswordAction.Change)

        assertEquals(AccountProblem.WRONG_CREDENTIALS, viewModel.uiState.value.problem)
        assertEquals("hunter22", accounts.passwords["sam@example.com"])
        assertTrue(events.isEmpty())
    }

    @Test
    fun `changing succeeds and says so`() = runTest {
        val viewModel = changePassword()
        viewModel.currentState.type("hunter22")
        viewModel.newState.type("newpass1")
        viewModel.confirmState.type("newpass1")

        viewModel.onAction(ChangePasswordAction.Change)

        assertEquals(listOf<ChangePasswordEvent>(ChangePasswordEvent.Changed), events)
        assertEquals("newpass1", accounts.passwords["sam@example.com"])
    }
}
