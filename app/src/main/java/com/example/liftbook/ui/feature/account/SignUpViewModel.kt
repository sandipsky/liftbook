package com.example.liftbook.ui.feature.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.Account
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
 * Creating an account (FR-8.1, FR-8.2): an email and a password typed twice, or Google. The
 * password's length is checked here, so the server is only asked with something it could accept.
 */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val emailState = TextFieldState()
    val passwordState = TextFieldState()
    val confirmState = TextFieldState()

    private val session = MutableStateFlow(Session())

    private val _events = Channel<SignUpEvent>(Channel.BUFFERED)
    val events: Flow<SignUpEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SignUpUiState> = combine(
        session,
        snapshotFlow { emailState.text.toString() },
        snapshotFlow { passwordState.text.toString() },
        snapshotFlow { confirmState.text.toString() },
    ) { session, email, password, confirm ->
        SignUpUiState(
            isAvailable = accounts.isAvailable,
            busy = session.busy,
            problem = session.problem,
            emailError = if (session.submitted && email.isBlank()) FieldError.BLANK else null,
            passwordError = if (session.submitted) passwordError(password) else null,
            confirmError = if (session.submitted && confirm != password) FieldError.MISMATCH else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignUpUiState(isAvailable = accounts.isAvailable))

    fun onAction(action: SignUpAction) {
        when (action) {
            SignUpAction.SignUp -> signUp()
            SignUpAction.GoogleStarted -> session.update { if (it.busy == null) it.copy(busy = SignInBusy.GOOGLE, problem = null) else it }
            is SignUpAction.GoogleToken -> attempt(SignInBusy.GOOGLE) { accounts.signInWithGoogle(action.idToken) }
            SignUpAction.GoogleCancelled -> session.update { if (it.busy == SignInBusy.GOOGLE) it.copy(busy = null) else it }
            is SignUpAction.GoogleFailed -> session.update { it.copy(busy = null, problem = action.problem) }
            // Navigation; handled by the route.
            SignUpAction.SignIn, SignUpAction.NavigateUp -> Unit
        }
    }

    private fun signUp() {
        if (session.value.busy != null) return
        session.update { it.copy(submitted = true) }
        val email = emailState.text.toString().trim()
        val password = passwordState.text.toString()
        if (email.isBlank() || passwordError(password) != null || confirmState.text.toString() != password) return
        attempt(SignInBusy.PASSWORD) { accounts.signUp(email, password) }
    }

    private fun attempt(busy: SignInBusy, signUp: suspend () -> Unit) {
        if (session.value.busy != null && session.value.busy != busy) return
        session.update { it.copy(busy = busy, problem = null) }
        viewModelScope.launch {
            try {
                signUp()
                welcomeSeen()
                _events.send(SignUpEvent.SignedIn)
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

    /** The first-launch page is done with (FR-8.8); if the flag can't be written it shows once more. */
    private suspend fun welcomeSeen() {
        try {
            settings.markWelcomeSeen()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // See above.
        }
    }

    private data class Session(
        val submitted: Boolean = false,
        val busy: SignInBusy? = null,
        val problem: AccountProblem? = null,
    )
}

/** Empty, or shorter than the server accepts (FR-8.1). */
internal fun passwordError(password: String): FieldError? = when {
    password.isEmpty() -> FieldError.BLANK
    password.length < Account.MIN_PASSWORD_LENGTH -> FieldError.TOO_SHORT
    else -> null
}
