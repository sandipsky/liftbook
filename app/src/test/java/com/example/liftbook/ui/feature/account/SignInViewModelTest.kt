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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accounts = FakeAccountRepository().apply { passwords["sam@example.com"] = "hunter22" }
    private val settings = FakeSettingsRepository()
    private val events = mutableListOf<SignInEvent>()

    private fun TestScope.signIn(): SignInViewModel = SignInViewModel(accounts, settings).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
    }

    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `an empty form says what's missing, and asks nothing of the server`() = runTest {
        val viewModel = signIn()

        viewModel.onAction(SignInAction.SignIn)

        val state = viewModel.uiState.value
        assertEquals(FieldError.BLANK, state.emailError)
        assertEquals(FieldError.BLANK, state.passwordError)
        assertNull(accounts.account.value)
        assertTrue(events.isEmpty())

        viewModel.emailState.type("sam@example.com")
        assertNull(viewModel.uiState.value.emailError)
    }

    @Test
    fun `a wrong password is reported under the form`() = runTest {
        val viewModel = signIn()
        viewModel.emailState.type("sam@example.com")
        viewModel.passwordState.type("nope")

        viewModel.onAction(SignInAction.SignIn)

        val state = viewModel.uiState.value
        assertEquals(AccountProblem.WRONG_CREDENTIALS, state.problem)
        assertNull(state.busy)
        assertNull(accounts.account.value)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `signing in succeeds and says so`() = runTest {
        val viewModel = signIn()
        viewModel.emailState.type(" sam@example.com ")
        viewModel.passwordState.type("hunter22")

        viewModel.onAction(SignInAction.SignIn)

        assertEquals(listOf<SignInEvent>(SignInEvent.SignedIn), events)
        assertEquals("sam@example.com", accounts.account.value?.email)
        assertTrue(settings.hasSeenWelcome.value)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun `continuing as a guest is remembered, so the page doesn't show again`() = runTest {
        val viewModel = signIn()

        viewModel.onAction(SignInAction.ContinueAsGuest)

        assertEquals(listOf<SignInEvent>(SignInEvent.ContinuedAsGuest), events)
        assertTrue(settings.hasSeenWelcome.value)
        assertNull(accounts.account.value)
    }

    @Test
    fun `google signs in with its token, and a closed picker leaves the form as it was`() = runTest {
        val viewModel = signIn()

        viewModel.onAction(SignInAction.GoogleStarted)
        assertEquals(SignInBusy.GOOGLE, viewModel.uiState.value.busy)
        viewModel.onAction(SignInAction.GoogleCancelled)
        assertNull(viewModel.uiState.value.busy)
        assertNull(viewModel.uiState.value.problem)

        viewModel.onAction(SignInAction.GoogleStarted)
        viewModel.onAction(SignInAction.GoogleToken("token"))

        assertEquals(listOf<SignInEvent>(SignInEvent.SignedIn), events)
        assertEquals("token@gmail.com", accounts.account.value?.email)
        assertNull(viewModel.uiState.value.busy)
    }

    @Test
    fun `a reset link goes to the email asked for`() = runTest {
        val viewModel = signIn()
        viewModel.emailState.type("sam@example.com")

        viewModel.onAction(SignInAction.OpenReset)
        assertNotNull(viewModel.uiState.value.reset)
        assertEquals("sam@example.com", viewModel.resetEmailState.text.toString())
        viewModel.onAction(SignInAction.SendReset)

        assertEquals(listOf<SignInEvent>(SignInEvent.ResetSent("sam@example.com")), events)
        assertEquals(listOf("sam@example.com"), accounts.resetsSent)
        assertNull(viewModel.uiState.value.reset)
    }

    @Test
    fun `a reset with no email asks for one`() = runTest {
        val viewModel = signIn()

        viewModel.onAction(SignInAction.OpenReset)
        viewModel.onAction(SignInAction.SendReset)

        assertEquals(FieldError.BLANK, viewModel.uiState.value.reset?.emailError)
        assertTrue(accounts.resetsSent.isEmpty())
    }
}
