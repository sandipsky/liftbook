package com.example.liftbook.ui.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.PrimaryActionButton
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

@Composable
fun ChangePasswordRoute(
    onClose: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChangePasswordEvent.Changed -> onChanged()
            }
        }
    }

    ChangePasswordScreen(
        state = state,
        currentState = viewModel.currentState,
        newState = viewModel.newState,
        confirmState = viewModel.confirmState,
        onAction = { action ->
            when (action) {
                ChangePasswordAction.Close -> onClose()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/** Change password (FR-8.4): three fields and one action, closing without saving if left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    currentState: TextFieldState,
    newState: TextFieldState,
    confirmState: TextFieldState,
    onAction: (ChangePasswordAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.change_password_title),
                navigation = TopBarNavigation.Close,
                onNavigationClick = { onAction(ChangePasswordAction.Close) },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.xs, bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.change_password_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            PasswordField(
                state = currentState,
                label = stringResource(R.string.change_password_current),
                error = state.currentError,
                enabled = !state.isChanging,
                blankMessageRes = R.string.account_error_current_blank,
            )
            Spacer(Modifier.height(Spacing.md))
            PasswordField(
                state = newState,
                label = stringResource(R.string.change_password_new),
                error = state.newError,
                enabled = !state.isChanging,
            )
            Spacer(Modifier.height(Spacing.xs))
            PasswordField(
                state = confirmState,
                label = stringResource(R.string.change_password_confirm),
                error = state.confirmError,
                enabled = !state.isChanging,
                imeAction = ImeAction.Done,
                onDone = { onAction(ChangePasswordAction.Change) },
            )
            Spacer(Modifier.height(Spacing.lg))
            state.problem?.let { FormProblem(it, Modifier.padding(bottom = Spacing.md)) }
            PrimaryActionButton(
                text = stringResource(if (state.isChanging) R.string.change_password_changing else R.string.change_password_action),
                onClick = { onAction(ChangePasswordAction.Change) },
                enabled = !state.isChanging,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ChangePasswordScreenPreview() {
    LiftBookTheme {
        ChangePasswordScreen(
            state = ChangePasswordUiState(problem = AccountProblem.WRONG_CREDENTIALS),
            currentState = rememberTextFieldState("hunter22"),
            newState = rememberTextFieldState(),
            confirmState = rememberTextFieldState(),
            onAction = {},
        )
    }
}
