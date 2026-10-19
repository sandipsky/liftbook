package com.example.liftbook.data.cloud

import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Runs one request against Firebase, bounded in time, and turns whatever it throws into an
 * [AccountException] the rest of the app can show. Firebase's own calls hang while the phone is
 * offline, and tell nobody, so the timeout is what reports "offline".
 */
internal suspend fun <T> firebaseCall(block: suspend () -> T): T = try {
    withTimeout(FIREBASE_TIMEOUT_MILLIS) { block() }
} catch (e: TimeoutCancellationException) {
    throw AccountException(AccountProblem.OFFLINE, e)
} catch (e: AccountException) {
    throw e
} catch (e: FirebaseAuthWeakPasswordException) {
    throw AccountException(AccountProblem.WEAK_PASSWORD, e)
} catch (e: FirebaseAuthInvalidCredentialsException) {
    val problem = if (e.errorCode == ERROR_INVALID_EMAIL) AccountProblem.INVALID_EMAIL else AccountProblem.WRONG_CREDENTIALS
    throw AccountException(problem, e)
} catch (e: FirebaseAuthInvalidUserException) {
    throw AccountException(AccountProblem.WRONG_CREDENTIALS, e)
} catch (e: FirebaseAuthUserCollisionException) {
    throw AccountException(AccountProblem.EMAIL_IN_USE, e)
} catch (e: FirebaseAuthRecentLoginRequiredException) {
    throw AccountException(AccountProblem.SIGN_IN_AGAIN, e)
} catch (e: FirebaseTooManyRequestsException) {
    throw AccountException(AccountProblem.TOO_MANY_ATTEMPTS, e)
} catch (e: FirebaseNetworkException) {
    throw AccountException(AccountProblem.OFFLINE, e)
} catch (e: FirebaseFirestoreException) {
    throw AccountException(e.problem(), e)
} catch (e: IOException) {
    throw AccountException(AccountProblem.OFFLINE, e)
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    throw AccountException(AccountProblem.UNKNOWN, e)
}

private fun FirebaseFirestoreException.problem(): AccountProblem = when (code) {
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
    -> AccountProblem.OFFLINE
    FirebaseFirestoreException.Code.PERMISSION_DENIED,
    FirebaseFirestoreException.Code.UNAUTHENTICATED,
    -> AccountProblem.NOT_ALLOWED
    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AccountProblem.TOO_MANY_ATTEMPTS
    else -> AccountProblem.UNKNOWN
}

private const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"

/** Long enough for a backup of a few megabytes on a slow connection; short enough to answer "offline" usefully. */
private const val FIREBASE_TIMEOUT_MILLIS = 60_000L
