package com.example.liftbook.ui.feature.account

import com.example.liftbook.domain.model.AccountProblem

data class SignInUiState(
    /** False without Firebase configuration: the form can't do anything. */
    val isAvailable: Boolean = true,
    /** What's under way, if anything; the form waits for it. */
    val busy: SignInBusy? = null,
    /** What the server answered the last attempt; cleared by the next. */
    val problem: AccountProblem? = null,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    /** The password-reset dialog, while it's open (FR-8.3). */
    val reset: ResetDraft? = null,
) {
    val isBusy: Boolean get() = busy != null
}

enum class SignInBusy { PASSWORD, GOOGLE }

data class ResetDraft(
    val isSending: Boolean = false,
    val problem: AccountProblem? = null,
    val emailError: FieldError? = null,
)

sealed interface SignInAction {
    data object SignIn : SignInAction

    /** The Google picker is opening; the screen reports how it ended with one of the three below. */
    data object GoogleStarted : SignInAction

    data class GoogleToken(val idToken: String) : SignInAction

    data object GoogleCancelled : SignInAction

    data class GoogleFailed(val problem: AccountProblem) : SignInAction

    data object OpenReset : SignInAction

    data object DismissReset : SignInAction

    data object SendReset : SignInAction

    /** Skip signing in, on the first launch (FR-8.8); the page doesn't show again. */
    data object ContinueAsGuest : SignInAction

    // Navigation; handled by the route.
    data object CreateAccount : SignInAction

    data object NavigateUp : SignInAction
}

sealed interface SignInEvent {
    data object SignedIn : SignInEvent

    data object ContinuedAsGuest : SignInEvent

    data class ResetSent(val email: String) : SignInEvent
}
