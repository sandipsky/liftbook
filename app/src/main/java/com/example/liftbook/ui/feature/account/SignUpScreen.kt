package com.example.liftbook.ui.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
fun SignUpRoute(
    onNavigateUp: () -> Unit,
    onSignIn: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val google = rememberGoogleSignIn(
        onToken = { viewModel.onAction(SignUpAction.GoogleToken(it)) },
        onCancelled = { viewModel.onAction(SignUpAction.GoogleCancelled) },
        onFailed = { viewModel.onAction(SignUpAction.GoogleFailed(it)) },
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignUpEvent.SignedIn -> onSignedIn()
            }
        }
    }

    SignUpScreen(
        state = state,
        emailState = viewModel.emailState,
        passwordState = viewModel.passwordState,
        confirmState = viewModel.confirmState,
        onAction = { action ->
            when (action) {
                SignUpAction.NavigateUp -> onNavigateUp()
                SignUpAction.SignIn -> onSignIn()
                SignUpAction.GoogleStarted -> {
                    viewModel.onAction(action)
                    google.start()
                }
                else -> viewModel.onAction(action)
            }
        },
    )
}

/** Create an account (FR-8.1): the sign-in form with the password confirmed, and nothing to verify by email. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    state: SignUpUiState,
    emailState: TextFieldState,
    passwordState: TextFieldState,
    confirmState: TextFieldState,
    onAction: (SignUpAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.sign_up_title),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(SignUpAction.NavigateUp) },
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
                text = stringResource(R.string.sign_up_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            EmailField(state = emailState, error = state.emailError, enabled = !state.isBusy)
            Spacer(Modifier.height(Spacing.xs))
            PasswordField(
                state = passwordState,
                label = stringResource(R.string.sign_in_password),
                error = state.passwordError,
                enabled = !state.isBusy,
            )
            Spacer(Modifier.height(Spacing.xs))
            PasswordField(
                state = confirmState,
                label = stringResource(R.string.sign_up_confirm_password),
                error = state.confirmError,
                enabled = !state.isBusy,
                imeAction = ImeAction.Done,
                onDone = { onAction(SignUpAction.SignUp) },
            )
            Spacer(Modifier.height(Spacing.lg))
            state.problem?.let { FormProblem(it, Modifier.padding(bottom = Spacing.md)) }
            PrimaryActionButton(
                text = stringResource(if (state.busy == SignInBusy.PASSWORD) R.string.sign_up_creating else R.string.sign_up_action),
                onClick = { onAction(SignUpAction.SignUp) },
                enabled = !state.isBusy,
            )
            OrDivider(Modifier.padding(vertical = Spacing.md))
            GoogleSignInButton(
                onClick = { onAction(SignUpAction.GoogleStarted) },
                enabled = !state.isBusy,
                busy = state.busy == SignInBusy.GOOGLE,
            )
            Spacer(Modifier.height(Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sign_up_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onAction(SignUpAction.SignIn) }, enabled = !state.isBusy) {
                    Text(stringResource(R.string.sign_up_sign_in))
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SignUpScreenPreview() {
    LiftBookTheme {
        SignUpScreen(
            state = SignUpUiState(),
            emailState = rememberTextFieldState(),
            passwordState = rememberTextFieldState(),
            confirmState = rememberTextFieldState(),
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun SignUpScreenErrorsPreview() {
    LiftBookTheme {
        SignUpScreen(
            state = SignUpUiState(problem = AccountProblem.EMAIL_IN_USE, passwordError = FieldError.TOO_SHORT, confirmError = FieldError.MISMATCH),
            emailState = rememberTextFieldState("sam@example.com"),
            passwordState = rememberTextFieldState("abc"),
            confirmState = rememberTextFieldState("abcd"),
            onAction = {},
        )
    }
}
