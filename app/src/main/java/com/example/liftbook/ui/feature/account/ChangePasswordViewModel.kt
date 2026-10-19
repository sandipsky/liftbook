package com.example.liftbook.ui.feature.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.repository.AccountRepository
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

/** Changing the password (FR-8.4): the current one proves it's the owner, the new one is typed twice. */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val accounts: AccountRepository,
) : ViewModel() {

    val currentState = TextFieldState()
    val newState = TextFieldState()
    val confirmState = TextFieldState()

    private val session = MutableStateFlow(Session())

    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events: Flow<ChangePasswordEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ChangePasswordUiState> = combine(
        session,
        snapshotFlow { currentState.text.toString() },
        snapshotFlow { newState.text.toString() },
        snapshotFlow { confirmState.text.toString() },
    ) { session, current, new, confirm ->
        ChangePasswordUiState(
            isChanging = session.isChanging,
            problem = session.problem,
            currentError = if (session.submitted && current.isEmpty()) FieldError.BLANK else null,
            newError = if (session.submitted) passwordError(new) else null,
            confirmError = if (session.submitted && confirm != new) FieldError.MISMATCH else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChangePasswordUiState())

    fun onAction(action: ChangePasswordAction) {
        when (action) {
            ChangePasswordAction.Change -> change()
            ChangePasswordAction.Close -> Unit
        }
    }

    private fun change() {
        if (session.value.isChanging) return
        session.update { it.copy(submitted = true) }
        val current = currentState.text.toString()
        val new = newState.text.toString()
        if (current.isEmpty() || passwordError(new) != null || confirmState.text.toString() != new) return
        session.update { it.copy(isChanging = true, problem = null) }
        viewModelScope.launch {
            try {
                accounts.changePassword(current, new)
                _events.send(ChangePasswordEvent.Changed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountException) {
                session.update { it.copy(problem = e.problem) }
            } catch (e: Exception) {
                session.update { it.copy(problem = AccountProblem.UNKNOWN) }
            } finally {
                session.update { it.copy(isChanging = false) }
            }
        }
    }

    private data class Session(
        val submitted: Boolean = false,
        val isChanging: Boolean = false,
        val problem: AccountProblem? = null,
    )
}
