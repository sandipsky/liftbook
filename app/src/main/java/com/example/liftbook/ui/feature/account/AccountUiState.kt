package com.example.liftbook.ui.feature.account

import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.model.CloudBackupStatus
import com.example.liftbook.domain.model.CloudSetup
import com.example.liftbook.domain.model.CloudSetupChoice
import com.example.liftbook.domain.model.DataCounts
import java.time.LocalDate
import java.time.ZoneId

data class AccountUiState(
    val isLoading: Boolean = true,
    /** False without Firebase configuration: nothing here can be used. */
    val isAvailable: Boolean = true,
    val account: Account? = null,
    /** Whether this phone's data is the signed-in account's (FR-8.6); until it is, nothing goes up or comes down. */
    val isOwner: Boolean = false,
    val backup: CloudBackupStatus = CloudBackupStatus(),
    /** What the account holds online; null while unknown, or when nothing. */
    val cloud: CloudBackupInfo? = null,
    val isCheckingCloud: Boolean = false,
    val hasCheckedCloud: Boolean = false,
    /** What's on this phone now. */
    val phone: DataCounts = DataCounts(),
    /** What's running, if anything; nothing else starts until it's done. */
    val operation: AccountOperation? = null,
    /** A question signing in raised, waiting for an answer (FR-8.6). */
    val setup: CloudSetup? = null,
    val isConfirmingRestore: Boolean = false,
    val isConfirmingSignOut: Boolean = false,
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isBusy: Boolean get() = operation != null || backup.isBackingUp

    /** Signed in, but the set-up never finished — it failed, or the question was put off. */
    val needsSetup: Boolean get() = account != null && !isOwner && setup == null && operation != AccountOperation.SETTING_UP
}

enum class AccountOperation { SETTING_UP, BACKING_UP, RESTORING, SIGNING_OUT }

sealed interface AccountAction {
    data object BackUpNow : AccountAction

    data object Restore : AccountAction

    data object ConfirmRestore : AccountAction

    data object DismissRestore : AccountAction

    data object SignOut : AccountAction

    data object ConfirmSignOut : AccountAction

    data object DismissSignOut : AccountAction

    /** Answers the set-up question (FR-8.6). */
    data class Choose(val choice: CloudSetupChoice) : AccountAction

    /** Puts the question off; it's asked again from the set-up row. */
    data object DismissSetup : AccountAction

    data object FinishSetup : AccountAction

    /** Whether backups follow changes on their own (FR-8.7). */
    data class SetAutoBackup(val enabled: Boolean) : AccountAction

    // Navigation; handled by the route.
    data object SignIn : AccountAction

    data object CreateAccount : AccountAction

    data object ChangePassword : AccountAction

    data object NavigateUp : AccountAction
}

sealed interface AccountEvent {
    data class BackedUp(val counts: DataCounts) : AccountEvent

    data class BackupFailed(val problem: AccountProblem) : AccountEvent

    data class Restored(val counts: DataCounts) : AccountEvent

    data class RestoreFailed(val problem: AccountProblem) : AccountEvent

    data class SetupFailed(val problem: AccountProblem) : AccountEvent

    data object SignedOut : AccountEvent

    data object SignOutFailed : AccountEvent

    /** The automatic-backup switch couldn't be saved. */
    data object SettingFailed : AccountEvent

    /** The change-password screen handed back that it worked (FR-8.4). */
    data object PasswordChanged : AccountEvent
}
