package com.example.liftbook.ui.feature.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.repository.AccountRepository
import com.example.liftbook.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Signing in (FR-8.1, FR-8.2) and asking for a password reset (FR-8.3). Fields are checked on
 * the phone first — only once Sign in is tapped, so an empty form doesn't open covered in errors
 * — and what the server answers is shown under the form until the next attempt.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val emailState = TextFieldState()
    val passwordState = TextFieldState()

    /** The reset dialog's email, started from whatever was typed above. */
    val resetEmailState = TextFieldState()

    private val session = MutableStateFlow(Session())

    private val _events = Channel<SignInEvent>(Channel.BUFFERED)
    val events: Flow<SignInEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SignInUiState> = combine(
        session,
        snapshotFlow { emailState.text.toString() },
        snapshotFlow { passwordState.text.toString() },
        snapshotFlow { resetEmailState.text.toString() },
    ) { session, email, password, resetEmail ->
        SignInUiState(
            isAvailable = accounts.isAvailable,
            busy = session.busy,
            problem = session.problem,
            emailError = if (session.submitted && email.isBlank()) FieldError.BLANK else null,
            passwordError = if (session.submitted && password.isEmpty()) FieldError.BLANK else null,
            reset = session.reset?.let { reset ->
                ResetDraft(
                    isSending = reset.isSending,
                    problem = reset.problem,
                    emailError = if (reset.submitted && resetEmail.isBlank()) FieldError.BLANK else null,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignInUiState(isAvailable = accounts.isAvailable))

    fun onAction(action: SignInAction) {
        when (action) {
            SignInAction.SignIn -> signIn()
            SignInAction.GoogleStarted -> session.update { if (it.busy == null) it.copy(busy = SignInBusy.GOOGLE, problem = null) else it }
            is SignInAction.GoogleToken -> attempt(SignInBusy.GOOGLE) { accounts.signInWithGoogle(action.idToken) }
            SignInAction.GoogleCancelled -> session.update { if (it.busy == SignInBusy.GOOGLE) it.copy(busy = null) else it }
            is SignInAction.GoogleFailed -> session.update { it.copy(busy = null, problem = action.problem) }
            SignInAction.OpenReset -> {
                resetEmailState.setTextAndPlaceCursorAtEnd(emailState.text.toString().trim())
                session.update { it.copy(reset = ResetSession()) }
            }
            SignInAction.DismissReset -> session.update { if (it.reset?.isSending == true) it else it.copy(reset = null) }
            SignInAction.SendReset -> sendReset()
            SignInAction.ContinueAsGuest -> continueAsGuest()
            // Navigation; handled by the route.
            SignInAction.CreateAccount, SignInAction.NavigateUp -> Unit
        }
    }

    private fun signIn() {
        if (session.value.busy != null) return
        session.update { it.copy(submitted = true) }
        val email = emailState.text.toString().trim()
        val password = passwordState.text.toString()
        if (email.isBlank() || password.isEmpty()) return
        attempt(SignInBusy.PASSWORD) { accounts.signIn(email, password) }
    }

    /** One attempt at a time; it ends signed in, or with what went wrong under the form. */
    private fun attempt(busy: SignInBusy, signIn: suspend () -> Unit) {
        if (session.value.busy != null && session.value.busy != busy) return
        session.update { it.copy(busy = busy, problem = null) }
        viewModelScope.launch {
            try {
                signIn()
                welcomeSeen()
                _events.send(SignInEvent.SignedIn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountException) {
                session.update { it.copy(problem = e.problem) }
            } catch (e: Exception) {
                session.update { it.copy(problem = AccountProblem.UNKNOWN) }
            } finally {
                session.update { it.copy(busy = null) }
            }
        }
    }

    /** The first-launch page is done with (FR-8.8). If the flag can't be written, the page shows once more; nothing worse. */
    private suspend fun welcomeSeen() {
        try {
            settings.markWelcomeSeen()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // See above.
        }
    }

    private fun continueAsGuest() {
        if (session.value.busy != null) return
        viewModelScope.launch {
            welcomeSeen()
            _events.send(SignInEvent.ContinuedAsGuest)
        }
    }

    private fun sendReset() {
        val reset = session.value.reset ?: return
        if (reset.isSending) return
        session.update { it.copy(reset = reset.copy(submitted = true)) }
        val email = resetEmailState.text.toString().trim()
        if (email.isBlank()) return
        session.update { it.copy(reset = it.reset?.copy(isSending = true, problem = null)) }
        viewModelScope.launch {
            try {
                accounts.sendPasswordReset(email)
                session.update { it.copy(reset = null) }
                _events.send(SignInEvent.ResetSent(email))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountException) {
                session.update { it.copy(reset = it.reset?.copy(isSending = false, problem = e.problem)) }
            } catch (e: Exception) {
                session.update { it.copy(reset = it.reset?.copy(isSending = false, problem = AccountProblem.UNKNOWN)) }
            }
        }
    }

    private data class Session(
        val submitted: Boolean = false,
        val busy: SignInBusy? = null,
        val problem: AccountProblem? = null,
        val reset: ResetSession? = null,
    )

    private data class ResetSession(
        val submitted: Boolean = false,
        val isSending: Boolean = false,
        val problem: AccountProblem? = null,
    )
}
