package com.example.liftbook.ui.feature.account

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.liftbook.R
import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/*
 * The account forms (FR-8.1–8.4) share their fields, so an email or a password reads the same
 * on every screen: an outlined field, the app's ink for the focused outline, and an error that
 * is a line of text under the field — never a colour alone.
 */

/** What's wrong with a field, found on the phone before the server is asked. */
enum class FieldError { BLANK, TOO_SHORT, MISMATCH }

@StringRes
fun AccountProblem.messageRes(): Int = when (this) {
    AccountProblem.NOT_CONFIGURED -> R.string.account_problem_not_configured
    AccountProblem.OFFLINE -> R.string.account_problem_offline
    AccountProblem.INVALID_EMAIL -> R.string.account_problem_invalid_email
    AccountProblem.WRONG_CREDENTIALS -> R.string.account_problem_wrong_credentials
    AccountProblem.EMAIL_IN_USE -> R.string.account_problem_email_in_use
    AccountProblem.WEAK_PASSWORD -> R.string.account_problem_weak_password
    AccountProblem.TOO_MANY_ATTEMPTS -> R.string.account_problem_too_many_attempts
    AccountProblem.SIGN_IN_AGAIN -> R.string.account_problem_sign_in_again
    AccountProblem.GOOGLE_UNAVAILABLE -> R.string.account_problem_google_unavailable
    AccountProblem.GOOGLE_NOT_CONFIGURED -> R.string.account_problem_google_not_configured
    AccountProblem.NOT_ALLOWED -> R.string.account_problem_not_allowed
    AccountProblem.NO_BACKUP -> R.string.account_problem_no_backup
    AccountProblem.NOT_SET_UP -> R.string.account_problem_not_set_up
    AccountProblem.SIGNED_OUT -> R.string.account_problem_signed_out
    AccountProblem.UNKNOWN -> R.string.account_problem_unknown
}

@Composable
fun EmailField(
    state: TextFieldState,
    error: FieldError?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.sign_in_email)) },
        isError = error != null,
        trailingIcon = if (error != null) {
            { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(IconSize.inline)) }
        } else {
            null
        },
        supportingText = error?.let { { Text(stringResource(R.string.account_error_email_blank)) } },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Email,
            imeAction = imeAction,
        ),
        onKeyboardAction = onDone?.let { done -> KeyboardActionHandler { done() } },
        shape = MaterialTheme.shapes.medium,
        colors = accountFieldColors(),
    )
}

/**
 * A password, hidden as typed with a toggle to show it. [blankMessageRes] names the field in its
 * "empty" message, since "Enter your password" and "Enter your current password" differ.
 */
@Composable
fun PasswordField(
    state: TextFieldState,
    label: String,
    error: FieldError?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    @StringRes blankMessageRes: Int = R.string.account_error_password_blank,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        isError = error != null,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.sign_in_hide_password else R.string.sign_in_show_password),
                    modifier = Modifier.size(IconSize.inline),
                )
            }
        },
        supportingText = error?.let { { Text(fieldErrorText(it, blankMessageRes)) } },
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        onKeyboardAction = onDone?.let { done -> KeyboardActionHandler { done() } },
        shape = MaterialTheme.shapes.medium,
        colors = accountFieldColors(),
    )
}

@Composable
private fun fieldErrorText(error: FieldError, @StringRes blankMessageRes: Int): String = when (error) {
    FieldError.BLANK -> stringResource(blankMessageRes)
    FieldError.TOO_SHORT -> pluralStringResource(R.plurals.account_error_password_short, Account.MIN_PASSWORD_LENGTH, Account.MIN_PASSWORD_LENGTH)
    FieldError.MISMATCH -> stringResource(R.string.account_error_password_mismatch)
}

@Composable
private fun accountFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.onSurface,
        unfocusedBorderColor = colors.outline,
        focusedLabelColor = colors.onSurface,
    )
}

/** What the server answered, as a line under the form. Announced as it appears. */
@Composable
fun FormProblem(problem: AccountProblem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(IconSize.inline),
        )
        Text(
            text = stringResource(problem.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Google's sign-in, as a quiet outlined button the height of the primary one: an alternative,
 * not the main action. The "G" keeps its own colours, as Google's branding asks.
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(ButtonOutline, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Icon(
            painterResource(R.drawable.ic_google_g),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(IconSize.inline),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(if (busy) R.string.sign_in_signing_in else R.string.sign_in_google),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** "or", between the password form and the Google button. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.sign_in_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

private val ButtonHeight = 56.dp
private val ButtonOutline = 1.dp

@ThemePreviews
@Composable
private fun AccountFormsPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            EmailField(rememberTextFieldState("sam@example.com"), error = null)
            PasswordField(rememberTextFieldState("hunter22"), label = "Password", error = FieldError.TOO_SHORT)
            FormProblem(AccountProblem.WRONG_CREDENTIALS)
            OrDivider()
            GoogleSignInButton(onClick = {})
        }
    }
}
