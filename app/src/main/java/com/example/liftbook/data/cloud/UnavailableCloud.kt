package com.example.liftbook.data.cloud

import android.content.Context
import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.model.BackupSnapshot
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.repository.AccountRepository
import com.example.liftbook.domain.repository.CloudBackupRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Whether this build was configured with a Firebase project. The google-services plugin writes
 * the project's keys into resources at build time, and Firebase initialises from them as the
 * app starts; a build made without the file has no app to initialise.
 */
object FirebaseAvailability {
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}

/** Stands in when there's no Firebase configuration: nobody is signed in, and nothing can be. */
object UnavailableAccountRepository : AccountRepository {
    override val isAvailable: Boolean = false
    override val account: Flow<Account?> = flowOf(null)
    override suspend fun signIn(email: String, password: String): Account = unavailable()
    override suspend fun signUp(email: String, password: String): Account = unavailable()
    override suspend fun signInWithGoogle(idToken: String): Account = unavailable()
    override suspend fun sendPasswordReset(email: String) = unavailable()
    override suspend fun changePassword(currentPassword: String, newPassword: String) = unavailable()
    override suspend fun signOut() = Unit
}

object UnavailableCloudBackupRepository : CloudBackupRepository {
    override suspend fun describe(accountId: String): CloudBackupInfo? = unavailable()
    override suspend fun upload(accountId: String, snapshot: BackupSnapshot) = unavailable()
    override suspend fun download(accountId: String): BackupSnapshot? = unavailable()
}

private fun unavailable(): Nothing = throw AccountException(AccountProblem.NOT_CONFIGURED)
