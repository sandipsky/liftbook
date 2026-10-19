package com.example.liftbook.domain.model

/**
 * The signed-in account (FR-8.1), as the app needs it: who, and whether a password is set. An
 * account is optional; everything works signed out.
 */
data class Account(
    val id: String,
    val email: String?,
    val displayName: String?,
    /** Whether the account has a password to change (FR-8.4); one made with Google alone has none. */
    val hasPassword: Boolean,
) {
    companion object {
        /** Firebase's floor; the sign-up and change-password forms check it before asking the server. */
        const val MIN_PASSWORD_LENGTH = 6
    }
}

/** Why signing in or up, changing a password, or reaching the cloud didn't happen (FR-8). */
enum class AccountProblem {
    /** This build has no Firebase configuration, so there's nothing to sign in to. */
    NOT_CONFIGURED,

    /** Nothing reached the server: offline, or it took too long. */
    OFFLINE,

    INVALID_EMAIL,

    /** A wrong password, or an unknown email: the server says the same for both, so it gives nothing away. */
    WRONG_CREDENTIALS,

    EMAIL_IN_USE,

    WEAK_PASSWORD,

    /** The server is rate-limiting this phone; wait before trying again. */
    TOO_MANY_ATTEMPTS,

    /** The sign-in is too old for a sensitive change; sign in again first (FR-8.4). */
    SIGN_IN_AGAIN,

    /** Google sign-in isn't possible here: no Google account on the phone, or no Play services. */
    GOOGLE_UNAVAILABLE,

    /** The build's Firebase configuration has no Google web client: Google sign-in wasn't registered for it. */
    GOOGLE_NOT_CONFIGURED,

    /** The server refused: the project's security rules don't allow it. */
    NOT_ALLOWED,

    /** The account has nothing stored online. */
    NO_BACKUP,

    /** The account hasn't settled whose data this phone holds yet (FR-8.6). */
    NOT_SET_UP,

    SIGNED_OUT,

    UNKNOWN,
}

class AccountException(val problem: AccountProblem, cause: Throwable? = null) : Exception("Account: $problem", cause)
