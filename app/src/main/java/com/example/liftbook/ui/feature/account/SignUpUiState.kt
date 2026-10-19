package com.example.liftbook.ui.feature.account

import com.example.liftbook.domain.model.AccountProblem

data class SignUpUiState(
    val isAvailable: Boolean = true,
    val busy: SignInBusy? = null,
    val problem: AccountProblem? = null,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val confirmError: FieldError? = null,
) {
    val isBusy: Boolean get() = busy != null
}

sealed interface SignUpAction {
    data object SignUp : SignUpAction

    data object GoogleStarted : SignUpAction

    data class GoogleToken(val idToken: String) : SignUpAction

    data object GoogleCancelled : SignUpAction

    data class GoogleFailed(val problem: AccountProblem) : SignUpAction

    // Navigation; handled by the route.
    data object SignIn : SignUpAction

    data object NavigateUp : SignUpAction
}

sealed interface SignUpEvent {
    data object SignedIn : SignUpEvent
}
