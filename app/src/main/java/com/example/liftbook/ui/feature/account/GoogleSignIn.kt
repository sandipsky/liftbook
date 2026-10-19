package com.example.liftbook.ui.feature.account

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Google sign-in (FR-8.2), through the phone's credential manager: the account picker is the
 * system's, and what comes back is an ID token for Firebase to check. The picker needs the
 * activity, so this lives with the screen; the ViewModel only ever sees the token.
 */
class GoogleSignIn internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onToken: (String) -> Unit,
    private val onCancelled: () -> Unit,
    private val onFailed: (AccountProblem) -> Unit,
) {
    /** Opens the account picker. Ends in exactly one of the three callbacks. */
    fun start() {
        scope.launch {
            try {
                val token = requestIdToken(context)
                if (token == null) onCancelled() else onToken(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountException) {
                onFailed(e.problem)
            }
        }
    }
}

@Composable
fun rememberGoogleSignIn(
    onToken: (String) -> Unit,
    onCancelled: () -> Unit,
    onFailed: (AccountProblem) -> Unit,
): GoogleSignIn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnToken by rememberUpdatedState(onToken)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    val currentOnFailed by rememberUpdatedState(onFailed)
    return remember(context, scope) {
        GoogleSignIn(context, scope, { currentOnToken(it) }, { currentOnCancelled() }, { currentOnFailed(it) })
    }
}

/** A Google ID token, or null if the user closed the picker. Throws [AccountException] otherwise. */
private suspend fun requestIdToken(context: Context): String? {
    val serverClientId = context.webClientId() ?: throw AccountException(AccountProblem.GOOGLE_NOT_CONFIGURED)
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
        .build()
    val result = try {
        CredentialManager.create(context).getCredential(context, request)
    } catch (e: GetCredentialCancellationException) {
        return null
    } catch (e: NoCredentialException) {
        throw AccountException(AccountProblem.GOOGLE_UNAVAILABLE, e)
    } catch (e: GetCredentialException) {
        throw AccountException(AccountProblem.GOOGLE_UNAVAILABLE, e)
    }
    val credential = result.credential
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        throw AccountException(AccountProblem.GOOGLE_UNAVAILABLE)
    }
    return try {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (e: GoogleIdTokenParsingException) {
        throw AccountException(AccountProblem.UNKNOWN, e)
    }
}

/**
 * The project's web client id, which the google-services plugin writes as a string resource
 * when Google sign-in is enabled. Looked up by name: a build without the configuration has no
 * such resource, and must still compile.
 */
@SuppressLint("DiscouragedApi")
private fun Context.webClientId(): String? {
    val id = resources.getIdentifier(WEB_CLIENT_ID_RESOURCE, "string", packageName)
    return if (id == 0) null else getString(id).takeIf { it.isNotBlank() }
}

private const val WEB_CLIENT_ID_RESOURCE = "default_web_client_id"
