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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

/**
 * Sign in. With [onContinueAsGuest] it is the first launch's page (FR-8.8): the root, with no
 * way back and the way past it instead.
 */
@Composable
fun SignInRoute(
    onNavigateUp: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignedIn: () -> Unit,
    onContinueAsGuest: (() -> Unit)? = null,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val google = rememberGoogleSignIn(
        onToken = { viewModel.onAction(SignInAction.GoogleToken(it)) },
        onCancelled = { viewModel.onAction(SignInAction.GoogleCancelled) },
        onFailed = { viewModel.onAction(SignInAction.GoogleFailed(it)) },
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignInEvent.SignedIn -> onSignedIn()
                SignInEvent.ContinuedAsGuest -> onContinueAsGuest?.invoke()
                is SignInEvent.ResetSent ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.reset_sent, event.email), duration = SnackbarDuration.Long)
            }
        }
    }

    SignInScreen(
        state = state,
        emailState = viewModel.emailState,
        passwordState = viewModel.passwordState,
        resetEmailState = viewModel.resetEmailState,
        snackbarHostState = snackbarHostState,
        isWelcome = onContinueAsGuest != null,
        onAction = { action ->
            when (action) {
                SignInAction.NavigateUp -> onNavigateUp()
                SignInAction.CreateAccount -> onCreateAccount()
                SignInAction.GoogleStarted -> {
                    viewModel.onAction(action)
                    google.start()
                }
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Sign in (FR-8.1, FR-8.2): email and password, then Google as the alternative, with the way to
 * a reset and to creating an account below. The one primary action sits right under the fields
 * and rides above the keyboard, so nothing has to be scrolled to while typing. On the first
 * launch ([isWelcome]) the page has no back action, says what an account is for, and offers to
 * go on without one (FR-8.8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    state: SignInUiState,
    emailState: TextFieldState,
    passwordState: TextFieldState,
    resetEmailState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onAction: (SignInAction) -> Unit,
    isWelcome: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.sign_in_title),
                navigation = if (isWelcome) TopBarNavigation.None else TopBarNavigation.Back,
                onNavigationClick = { onAction(SignInAction.NavigateUp) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                text = stringResource(if (isWelcome) R.string.sign_in_welcome_body else R.string.sign_in_body),
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
                imeAction = ImeAction.Done,
                onDone = { onAction(SignInAction.SignIn) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onAction(SignInAction.OpenReset) }, enabled = !state.isBusy) {
                    Text(stringResource(R.string.sign_in_forgot))
                }
            }
            state.problem?.let { FormProblem(it, Modifier.padding(bottom = Spacing.md)) }
            PrimaryActionButton(
                text = stringResource(if (state.busy == SignInBusy.PASSWORD) R.string.sign_in_signing_in else R.string.sign_in_action),
                onClick = { onAction(SignInAction.SignIn) },
                enabled = !state.isBusy,
            )
            OrDivider(Modifier.padding(vertical = Spacing.md))
            GoogleSignInButton(
                onClick = { onAction(SignInAction.GoogleStarted) },
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
                    text = stringResource(R.string.sign_in_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onAction(SignInAction.CreateAccount) }, enabled = !state.isBusy) {
                    Text(stringResource(R.string.sign_in_create_account))
                }
            }
            if (isWelcome) {
                Spacer(Modifier.height(Spacing.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { onAction(SignInAction.ContinueAsGuest) }, enabled = !state.isBusy) {
                        Text(stringResource(R.string.sign_in_guest))
                    }
                }
            }
        }
    }

    state.reset?.let { reset ->
        ResetPasswordDialog(
            draft = reset,
            emailState = resetEmailState,
            onSend = { onAction(SignInAction.SendReset) },
            onDismiss = { onAction(SignInAction.DismissReset) },
        )
    }
}

/** Asks for the email to send a reset link to (FR-8.3). */
@Composable
private fun ResetPasswordDialog(
    draft: ResetDraft,
    emailState: TextFieldState,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.reset_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                EmailField(
                    state = emailState,
                    error = draft.emailError,
                    enabled = !draft.isSending,
                    imeAction = ImeAction.Send,
                    onDone = onSend,
                )
                draft.problem?.let { FormProblem(it, Modifier.padding(top = Spacing.xs)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !draft.isSending) {
                Text(stringResource(R.string.reset_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !draft.isSending) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@ThemePreviews
@Composable
private fun SignInScreenPreview() {
    LiftBookTheme {
        SignInScreen(
            state = SignInUiState(),
            emailState = rememberTextFieldState("sam@example.com"),
            passwordState = rememberTextFieldState(),
            resetEmailState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun SignInScreenProblemPreview() {
    LiftBookTheme {
        SignInScreen(
            state = SignInUiState(problem = AccountProblem.WRONG_CREDENTIALS, passwordError = FieldError.BLANK),
            emailState = rememberTextFieldState("sam@example.com"),
            passwordState = rememberTextFieldState(),
            resetEmailState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun SignInScreenWelcomePreview() {
    LiftBookTheme {
        SignInScreen(
            state = SignInUiState(),
            emailState = rememberTextFieldState(),
            passwordState = rememberTextFieldState(),
            resetEmailState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
            isWelcome = true,
        )
    }
}
