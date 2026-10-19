package com.example.liftbook.data.cloud

import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.repository.AccountRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** The account, in Firebase Authentication (FR-8.1–8.4). */
class FirebaseAccountRepository(private val auth: FirebaseAuth) : AccountRepository {

    override val isAvailable: Boolean = true

    override val account: Flow<Account?> = callbackFlow {
        // Fires once on registration with the current state, then on every change.
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): Account = firebaseCall {
        auth.signInWithEmailAndPassword(email.trim(), password).await().user.orSignedOut()
    }

    override suspend fun signUp(email: String, password: String): Account = firebaseCall {
        auth.createUserWithEmailAndPassword(email.trim(), password).await().user.orSignedOut()
    }

    override suspend fun signInWithGoogle(idToken: String): Account = firebaseCall {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user.orSignedOut()
    }

    override suspend fun sendPasswordReset(email: String) {
        firebaseCall { auth.sendPasswordResetEmail(email.trim()).await() }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        firebaseCall {
            val user = auth.currentUser ?: throw AccountException(AccountProblem.SIGNED_OUT)
            val email = user.email ?: throw AccountException(AccountProblem.UNKNOWN)
            // Proves the current password, and freshens the sign-in that a password change requires.
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
            user.updatePassword(newPassword).await()
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private fun FirebaseUser?.orSignedOut(): Account = this?.toAccount() ?: throw AccountException(AccountProblem.SIGNED_OUT)

    private fun FirebaseUser.toAccount() = Account(
        id = uid,
        email = email,
        displayName = displayName?.takeIf { it.isNotBlank() },
        hasPassword = providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID },
    )
}
