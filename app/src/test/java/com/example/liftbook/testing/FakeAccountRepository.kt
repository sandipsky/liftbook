package com.example.liftbook.testing

import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Accounts in memory: an email and a password each, and whoever is signed in. */
class FakeAccountRepository(
    initial: Account? = null,
    override val isAvailable: Boolean = true,
) : AccountRepository {

    override val account = MutableStateFlow(initial)

    /** Email to password, for signing in and up. */
    val passwords = mutableMapOf<String, String>()

    val resetsSent = mutableListOf<String>()

    var signOuts = 0
        private set

    /** When set, the next call that reaches the server throws it. */
    var failure: AccountException? = null

    override suspend fun signIn(email: String, password: String): Account {
        fail()
        if (passwords[email.trim()] != password) throw AccountException(AccountProblem.WRONG_CREDENTIALS)
        return signedIn(email.trim(), hasPassword = true)
    }

    override suspend fun signUp(email: String, password: String): Account {
        fail()
        if (email.trim() in passwords) throw AccountException(AccountProblem.EMAIL_IN_USE)
        if (password.length < Account.MIN_PASSWORD_LENGTH) throw AccountException(AccountProblem.WEAK_PASSWORD)
        passwords[email.trim()] = password
        return signedIn(email.trim(), hasPassword = true)
    }

    override suspend fun signInWithGoogle(idToken: String): Account {
        fail()
        return signedIn("$idToken@gmail.com", hasPassword = false)
    }

    override suspend fun sendPasswordReset(email: String) {
        fail()
        resetsSent += email.trim()
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        fail()
        val email = account.value?.email ?: throw AccountException(AccountProblem.SIGNED_OUT)
        if (passwords[email] != currentPassword) throw AccountException(AccountProblem.WRONG_CREDENTIALS)
        passwords[email] = newPassword
    }

    override suspend fun signOut() {
        signOuts++
        account.value = null
    }

    private fun signedIn(email: String, hasPassword: Boolean): Account = account(email, hasPassword).also { account.value = it }

    private fun fail() {
        failure?.let {
            failure = null
            throw it
        }
    }

    companion object {
        fun account(email: String = "sam@example.com", hasPassword: Boolean = true) =
            Account(id = "uid-$email", email = email, displayName = null, hasPassword = hasPassword)
    }
}
