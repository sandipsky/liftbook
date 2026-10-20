package com.example.liftbook.ui.feature.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.AccountException
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.model.CloudSetup
import com.example.liftbook.domain.model.CloudSetupChoice
import com.example.liftbook.domain.repository.AccountRepository
import com.example.liftbook.domain.repository.BackupRepository
import com.example.liftbook.domain.usecase.CloudBackups
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The account page (FR-8): who is signed in, the cloud backup and its state, and the ways to
 * sign in, change the password and sign out. It's where signing in lands, so it's also where
 * whose data the phone holds is settled (FR-8.6): as soon as an account is signed in that the
 * data doesn't belong to, the question is asked here, and asked again if it was put off.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val cloudBackups: CloudBackups,
    private val backupRepository: BackupRepository,
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {

    private val session = MutableStateFlow(Session())

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events: Flow<AccountEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<AccountUiState> = combine(
        accounts.account,
        cloudBackups.status,
        backupRepository.cloudState,
        backupRepository.observeCounts(),
        session,
    ) { account, status, cloudState, phone, session ->
        AccountUiState(
            isLoading = false,
            isAvailable = accounts.isAvailable,
            account = account,
            isOwner = account != null && cloudState.ownerAccountId == account.id,
            backup = status,
            cloud = session.cloud,
            isCheckingCloud = session.isCheckingCloud,
            hasCheckedCloud = session.hasCheckedCloud,
            phone = phone,
            operation = session.operation,
            setup = session.setup,
            isConfirmingRestore = session.isConfirmingRestore,
            isConfirmingSignOut = session.isConfirmingSignOut,
            today = LocalDate.now(clock),
            zone = clock.zone,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountUiState(isAvailable = accounts.isAvailable, today = LocalDate.now(clock), zone = clock.zone),
    )

    init {
        // Whose data this phone holds is settled as soon as an account it isn't signed in (FR-8.6).
        viewModelScope.launch {
            combine(accounts.account, backupRepository.cloudState) { account, state ->
                account?.id?.takeIf { state.ownerAccountId != it }
            }.distinctUntilChanged().collect { accountId ->
                if (accountId != null) setUp()
            }
        }
        // What's online, for the restore row: looked up on signing in, and again after each backup.
        viewModelScope.launch {
            combine(
                accounts.account.map { it?.id }.distinctUntilChanged(),
                cloudBackups.status.map { it.lastBackupAt }.distinctUntilChanged(),
            ) { accountId, _ -> accountId }
                .collectLatest { accountId ->
                    if (accountId == null) session.update { it.copy(cloud = null, hasCheckedCloud = false) } else checkCloud()
                }
        }
        // The change-password screen hands back that it succeeded (FR-8.4).
        viewModelScope.launch {
            savedStateHandle.getStateFlow(PASSWORD_CHANGED, false)
                .filter { it }
                .collect {
                    savedStateHandle[PASSWORD_CHANGED] = false
                    _events.send(AccountEvent.PasswordChanged)
                }
        }
    }

    fun onAction(action: AccountAction) {
        when (action) {
            AccountAction.BackUpNow -> perform(AccountOperation.BACKING_UP, failure = AccountEvent::BackupFailed) {
                cloudBackups.backUpNow()
                AccountEvent.BackedUp(backupRepository.observeCounts().first())
            }
            AccountAction.Restore -> if (!session.value.isBusy) session.update { it.copy(isConfirmingRestore = true) }
            AccountAction.ConfirmRestore -> {
                session.update { it.copy(isConfirmingRestore = false) }
                perform(AccountOperation.RESTORING, failure = AccountEvent::RestoreFailed) {
                    AccountEvent.Restored(cloudBackups.restore())
                }
            }
            AccountAction.DismissRestore -> session.update { it.copy(isConfirmingRestore = false) }
            AccountAction.SignOut -> if (!session.value.isBusy) session.update { it.copy(isConfirmingSignOut = true) }
            AccountAction.ConfirmSignOut -> {
                session.update { it.copy(isConfirmingSignOut = false, setup = null) }
                signOut()
            }
            AccountAction.DismissSignOut -> session.update { it.copy(isConfirmingSignOut = false) }
            is AccountAction.Choose -> {
                session.update { it.copy(setup = null) }
                perform(AccountOperation.SETTING_UP, failure = AccountEvent::SetupFailed) {
                    cloudBackups.resolve(action.choice)?.let { AccountEvent.Restored(it) }
                }
            }
            AccountAction.DismissSetup -> session.update { it.copy(setup = null) }
            AccountAction.FinishSetup -> setUp()
            is AccountAction.SetAutoBackup -> viewModelScope.launch {
                try {
                    backupRepository.setCloudAutoBackup(action.enabled)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _events.send(AccountEvent.SettingFailed)
                }
            }
            // Navigation; handled by the route.
            AccountAction.SignIn, AccountAction.CreateAccount, AccountAction.ChangePassword, AccountAction.NavigateUp -> Unit
        }
    }

    private fun setUp() = perform(AccountOperation.SETTING_UP, failure = AccountEvent::SetupFailed) {
        when (val setup = cloudBackups.prepare()) {
            CloudSetup.Done -> null
            // An empty phone took the account's backup without asking; say what came down.
            is CloudSetup.Restored -> AccountEvent.Restored(setup.counts)
            is CloudSetup.ChooseSource, is CloudSetup.OtherAccountData -> {
                session.update { it.copy(setup = setup) }
                null
            }
        }
    }

    private fun signOut() = perform(AccountOperation.SIGNING_OUT, failure = { AccountEvent.SignOutFailed }) {
        accounts.signOut()
        AccountEvent.SignedOut
    }

    private suspend fun checkCloud() {
        session.update { it.copy(isCheckingCloud = true) }
        val info: CloudBackupInfo? = try {
            cloudBackups.describe()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountException) {
            null
        }
        session.update { it.copy(cloud = info, isCheckingCloud = false, hasCheckedCloud = true) }
    }

    /** Runs [work] unless something else is running. Leaving the page doesn't cut a restore short. */
    private fun perform(operation: AccountOperation, failure: (AccountProblem) -> AccountEvent, work: suspend () -> AccountEvent?) {
        if (session.value.isBusy) return
        session.update { it.copy(operation = operation) }
        viewModelScope.launch {
            val event = withContext(NonCancellable) {
                try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AccountException) {
                    failure(e.problem)
                } catch (e: Exception) {
                    failure(AccountProblem.UNKNOWN)
                } finally {
                    session.update { it.copy(operation = null) }
                }
            }
            event?.let { _events.send(it) }
        }
    }

    private data class Session(
        val operation: AccountOperation? = null,
        val cloud: CloudBackupInfo? = null,
        val isCheckingCloud: Boolean = false,
        val hasCheckedCloud: Boolean = false,
        val setup: CloudSetup? = null,
        val isConfirmingRestore: Boolean = false,
        val isConfirmingSignOut: Boolean = false,
    ) {
        val isBusy: Boolean get() = operation != null
    }

    companion object {
        /** Set on this destination's SavedStateHandle by the change-password screen, on success. */
        const val PASSWORD_CHANGED = "passwordChanged"
    }
}
