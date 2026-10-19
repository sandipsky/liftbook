package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountException
import kotlinx.coroutines.flow.Flow

/**
 * The optional account (FR-8.1–8.4). Every call that reaches the server throws
 * [AccountException] when it can't be done, saying why.
 */
interface AccountRepository {

    /** False when this build has no Firebase configuration: nothing here can be used. */
    val isAvailable: Boolean

    /** The signed-in account, or null; follows sign-ins and sign-outs. */
    val account: Flow<Account?>

    suspend fun signIn(email: String, password: String): Account

    /** Creates the account and signs in. No email to confirm (FR-8.1). */
    suspend fun signUp(email: String, password: String): Account

    /** With an ID token from the phone's credential manager (FR-8.2). */
    suspend fun signInWithGoogle(idToken: String): Account

    /** Emails a link to choose a new password (FR-8.3). Says nothing about whether the email has an account. */
    suspend fun sendPasswordReset(email: String)

    /** Checks [currentPassword] first, so an unattended phone can't have its password changed (FR-8.4). */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    suspend fun signOut()
}
