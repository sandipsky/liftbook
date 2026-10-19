package com.example.liftbook.ui.feature.account

import com.example.liftbook.domain.model.AccountProblem

data class ChangePasswordUiState(
    val isChanging: Boolean = false,
    val problem: AccountProblem? = null,
    val currentError: FieldError? = null,
    val newError: FieldError? = null,
    val confirmError: FieldError? = null,
)

sealed interface ChangePasswordAction {
    data object Change : ChangePasswordAction

    // Navigation; handled by the route.
    data object Close : ChangePasswordAction
}

sealed interface ChangePasswordEvent {
    data object Changed : ChangePasswordEvent
}
