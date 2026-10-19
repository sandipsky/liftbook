package com.example.liftbook.ui.feature.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.testing.FakeAccountRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accounts = FakeAccountRepository()
    private val events = mutableListOf<SignUpEvent>()

    private fun TestScope.signUp(): SignUpViewModel = SignUpViewModel(accounts, FakeSettingsRepository()).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
    }

    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `a short password and a mismatch are caught before the server is asked`() = runTest {
        val viewModel = signUp()
        viewModel.emailState.type("sam@example.com")
        viewModel.passwordState.type("abc")
        viewModel.confirmState.type("abcd")

        viewModel.onAction(SignUpAction.SignUp)

        val state = viewModel.uiState.value
        assertNull(state.emailError)
        assertEquals(FieldError.TOO_SHORT, state.passwordError)
        assertEquals(FieldError.MISMATCH, state.confirmError)
        assertTrue(accounts.passwords.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `an email already in use is reported`() = runTest {
        accounts.passwords["sam@example.com"] = "hunter22"
        val viewModel = signUp()
        viewModel.emailState.type("sam@example.com")
        viewModel.passwordState.type("another1")
        viewModel.confirmState.type("another1")

        viewModel.onAction(SignUpAction.SignUp)

        assertEquals(AccountProblem.EMAIL_IN_USE, viewModel.uiState.value.problem)
        assertNull(accounts.account.value)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `creating an account signs in`() = runTest {
        val viewModel = signUp()
        viewModel.emailState.type("sam@example.com")
        viewModel.passwordState.type("hunter22")
        viewModel.confirmState.type("hunter22")

        viewModel.onAction(SignUpAction.SignUp)

        assertEquals(listOf<SignUpEvent>(SignUpEvent.SignedIn), events)
        assertEquals("hunter22", accounts.passwords["sam@example.com"])
        assertEquals("sam@example.com", accounts.account.value?.email)
    }

    @Test
    fun `google creates the account with its token`() = runTest {
        val viewModel = signUp()

        viewModel.onAction(SignUpAction.GoogleStarted)
        viewModel.onAction(SignUpAction.GoogleToken("token"))

        assertEquals(listOf<SignUpEvent>(SignUpEvent.SignedIn), events)
        assertEquals("token@gmail.com", accounts.account.value?.email)
    }
}
