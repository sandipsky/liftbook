package com.example.liftbook.ui.feature.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftbook.R
import com.example.liftbook.domain.model.Account
import com.example.liftbook.domain.model.AccountProblem
import com.example.liftbook.domain.model.CloudBackupInfo
import com.example.liftbook.domain.model.CloudBackupStatus
import com.example.liftbook.domain.model.CloudSetup
import com.example.liftbook.domain.model.CloudSetupChoice
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.ui.components.ConfirmDialog
import com.example.liftbook.ui.components.EmptyState
import com.example.liftbook.ui.components.Fact
import com.example.liftbook.ui.components.LiftBookTopBar
import com.example.liftbook.ui.components.PrimaryActionButton
import com.example.liftbook.ui.components.SectionHeader
import com.example.liftbook.ui.components.SettingsRow
import com.example.liftbook.ui.components.SettingsSwitchRow
import com.example.liftbook.ui.components.SkeletonBlock
import com.example.liftbook.ui.components.SkeletonContainer
import com.example.liftbook.ui.components.TopBarNavigation
import com.example.liftbook.ui.feature.settings.backupDayText
import com.example.liftbook.ui.feature.settings.dataCountsText
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookTheme
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun AccountRoute(
    onNavigateUp: () -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onChangePassword: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is AccountEvent.BackedUp -> resources.getString(R.string.account_backed_up, resources.countsText(event.counts))
                is AccountEvent.BackupFailed -> resources.getString(R.string.account_backup_failed, resources.getString(event.problem.messageRes()))
                is AccountEvent.Restored -> resources.getString(R.string.account_restored, resources.countsText(event.counts))
                is AccountEvent.RestoreFailed -> resources.getString(R.string.account_restore_failed, resources.getString(event.problem.messageRes()))
                is AccountEvent.SetupFailed -> resources.getString(R.string.account_setup_failed, resources.getString(event.problem.messageRes()))
                AccountEvent.SignedOut -> resources.getString(R.string.account_signed_out)
                AccountEvent.SignOutFailed -> resources.getString(R.string.account_sign_out_failed)
                AccountEvent.SettingFailed -> resources.getString(R.string.settings_save_failed)
                AccountEvent.PasswordChanged -> resources.getString(R.string.account_password_changed)
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    AccountScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                AccountAction.NavigateUp -> onNavigateUp()
                AccountAction.SignIn -> onSignIn()
                AccountAction.CreateAccount -> onCreateAccount()
                AccountAction.ChangePassword -> onChangePassword()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Account (FR-8). Signed out, it says what an account adds and offers the two ways in. Signed
 * in, it leads with who, then the cloud backup — when it was last made, whether it's current,
 * and the one action that makes one — with restoring, the password and signing out as rows
 * below. The questions signing in can raise (FR-8.6) are asked here, over the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (AccountAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val headlineScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val content = when {
        state.isLoading -> AccountContent.Loading
        !state.isAvailable -> AccountContent.Unavailable
        state.account == null -> AccountContent.SignedOut
        else -> AccountContent.SignedIn
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LiftBookTopBar(
                title = stringResource(R.string.account_title),
                navigation = TopBarNavigation.Back,
                onNavigationClick = { onAction(AccountAction.NavigateUp) },
                showTitle = content != AccountContent.SignedIn || headlineScrolledAway,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = content,
            transitionSpec = { fadeIn(tween(CONTENT_FADE_MILLIS)) togetherWith fadeOut(tween(CONTENT_FADE_MILLIS)) },
            label = "accountContent",
        ) { target ->
            when (target) {
                AccountContent.Loading -> AccountSkeleton(Modifier.padding(padding))
                AccountContent.Unavailable -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.account_unavailable_title),
                    body = stringResource(R.string.account_unavailable_body),
                    modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()),
                )
                AccountContent.SignedOut -> SignedOut(onAction = onAction, modifier = Modifier.padding(padding))
                AccountContent.SignedIn -> SignedIn(state = state, listState = listState, padding = padding, onAction = onAction)
            }
        }
    }

    state.setup?.let { setup ->
        state.account?.let { account ->
            SetupDialog(setup = setup, account = account, today = state.today, zone = state.zone, onAction = onAction)
        }
    }

    if (state.isConfirmingRestore) {
        val cloud = state.cloud
        ConfirmDialog(
            title = stringResource(R.string.account_restore_dialog_title),
            text = stringResource(
                R.string.account_restore_dialog_body,
                cloud?.let { backupDayText(it.savedAt, state.today, state.zone) }.orEmpty(),
                countsText(cloud?.counts ?: DataCounts()),
            ),
            confirmLabel = stringResource(R.string.account_restore_dialog_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { onAction(AccountAction.ConfirmRestore) },
            onDismiss = { onAction(AccountAction.DismissRestore) },
            destructive = true,
        )
    }

    if (state.isConfirmingSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.account_sign_out_dialog_title),
            text = stringResource(
                if (state.backup.isPending) R.string.account_sign_out_dialog_body_pending else R.string.account_sign_out_dialog_body,
            ),
            confirmLabel = stringResource(R.string.account_sign_out),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { onAction(AccountAction.ConfirmSignOut) },
            onDismiss = { onAction(AccountAction.DismissSignOut) },
        )
    }
}

private enum class AccountContent { Loading, Unavailable, SignedOut, SignedIn }

@Composable
private fun SignedOut(onAction: (AccountAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs, bottom = Spacing.xl),
    ) {
        Text(
            text = stringResource(R.string.account_signed_out_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        PrimaryActionButton(text = stringResource(R.string.account_sign_in), onClick = { onAction(AccountAction.SignIn) })
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(
            onClick = { onAction(AccountAction.CreateAccount) },
            modifier = Modifier.fillMaxWidth().heightIn(min = SecondaryActionHeight),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(stringResource(R.string.account_create), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SignedIn(
    state: AccountUiState,
    listState: LazyListState,
    padding: PaddingValues,
    onAction: (AccountAction) -> Unit,
) {
    val account = state.account ?: return
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(key = "header", contentType = "header") {
            Text(
                text = stringResource(R.string.account_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = gutter
                    .padding(top = Spacing.xs, bottom = Spacing.md)
                    .semantics { heading() },
            )
        }
        item(key = "who", contentType = "panel") {
            WhoPanel(account, gutter)
        }
        if (state.needsSetup) {
            item(key = "setup", contentType = "panel") {
                SetupPanel(isBusy = state.isBusy, onAction = onAction, modifier = gutter.padding(top = Spacing.xs))
            }
        }

        item(key = "backupHeader", contentType = "sectionHeader") {
            SectionHeader(
                title = stringResource(R.string.account_section_backup),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
            )
        }
        item(key = "backup", contentType = "panel") {
            BackupPanel(state, onAction, gutter)
        }
        item(key = "autoBackup", contentType = "switch") {
            SettingsSwitchRow(
                title = stringResource(R.string.account_auto_backup_title),
                supporting = stringResource(R.string.account_auto_backup_body),
                checked = state.backup.autoBackup,
                onCheckedChange = { onAction(AccountAction.SetAutoBackup(it)) },
                modifier = gutter,
            )
        }
        item(key = "restore", contentType = "row") {
            val cloud = state.cloud
            SettingsRow(
                title = stringResource(R.string.account_restore_title),
                supporting = when {
                    cloud != null -> stringResource(R.string.account_restore_body, backupDayText(cloud.savedAt, state.today, state.zone))
                    state.hasCheckedCloud -> stringResource(R.string.account_restore_body_none)
                    else -> stringResource(R.string.account_restore_body_checking)
                },
                icon = Icons.Outlined.CloudDownload,
                onClick = { onAction(AccountAction.Restore) },
                clickLabel = stringResource(R.string.account_restore_click_label),
                enabled = cloud != null && state.isOwner && !state.isBusy,
                modifier = gutter,
            )
        }

        if (account.hasPassword) {
            item(key = "securityHeader", contentType = "sectionHeader") {
                SectionHeader(
                    title = stringResource(R.string.account_section_security),
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs),
                )
            }
            item(key = "password", contentType = "row") {
                SettingsRow(
                    title = stringResource(R.string.account_change_password),
                    supporting = stringResource(R.string.account_change_password_body),
                    icon = Icons.Outlined.Password,
                    showsChevron = true,
                    onClick = { onAction(AccountAction.ChangePassword) },
                    clickLabel = stringResource(R.string.account_change_password_click_label),
                    enabled = !state.isBusy,
                    modifier = gutter,
                )
            }
        }

        item(key = "signOut", contentType = "row") {
            SettingsRow(
                title = stringResource(R.string.account_sign_out),
                supporting = stringResource(R.string.account_sign_out_body),
                icon = Icons.AutoMirrored.Outlined.Logout,
                onClick = { onAction(AccountAction.SignOut) },
                clickLabel = stringResource(R.string.account_sign_out_click_label),
                enabled = !state.isBusy,
                modifier = gutter.padding(top = Spacing.lg),
            )
        }
    }
}

/** Who is signed in, and how. */
@Composable
private fun WhoPanel(account: Account, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(Spacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = stringResource(R.string.account_signed_in_as),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = account.displayName ?: account.email.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        if (account.displayName != null && account.email != null) {
            Text(text = account.email, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = stringResource(if (account.hasPassword) R.string.account_with_password else R.string.account_with_google),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/** The set-up never finished: it failed, or its question was put off. Here's the way back to it. */
@Composable
private fun SetupPanel(isBusy: Boolean, onAction: (AccountAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.account_setup_needed_title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = stringResource(R.string.account_setup_needed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = { onAction(AccountAction.FinishSetup) }, enabled = !isBusy) {
            Text(stringResource(R.string.account_setup_needed_action))
        }
    }
}

/** The cloud backup: when it was last made, whether it's current, and the action that makes one. */
@Composable
private fun BackupPanel(state: AccountUiState, onAction: (AccountAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val backup = state.backup
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.account_backup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Fact(
            label = stringResource(R.string.account_backup_last),
            value = backup.lastBackupAt?.let { backupDayText(it, state.today, state.zone) } ?: stringResource(R.string.account_backup_never),
            modifier = Modifier.padding(top = Spacing.md),
        )
        BackupStatusLine(backup = backup, isOwner = state.isOwner, modifier = Modifier.padding(top = Spacing.xs))
        Spacer(Modifier.height(Spacing.md))
        Button(
            onClick = { onAction(AccountAction.BackUpNow) },
            enabled = state.isOwner && !state.isBusy,
        ) {
            Text(stringResource(if (backup.isBackingUp) R.string.account_backup_running else R.string.account_back_up_now))
        }
    }
}

@Composable
private fun BackupStatusLine(backup: CloudBackupStatus, isOwner: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val failure = backup.lastFailure
    when {
        backup.isBackingUp -> StatusText(stringResource(R.string.account_backup_running), modifier)
        failure != null -> Row(
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(IconSize.inline))
            Text(
                text = stringResource(R.string.account_backup_failed, stringResource(failure.messageRes())),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
        }
        !isOwner -> Unit
        backup.isWaitingForWorkout -> StatusText(stringResource(R.string.account_backup_waiting_workout), modifier)
        backup.isPending -> StatusText(
            stringResource(if (backup.autoBackup) R.string.account_backup_pending else R.string.account_backup_pending_auto_off),
            modifier,
        )
        backup.lastBackupAt != null -> StatusText(stringResource(R.string.account_backup_up_to_date), modifier)
    }
}

@Composable
private fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The question signing in raised (FR-8.6), with its two answers. Closing it puts it off. */
@Composable
private fun SetupDialog(
    setup: CloudSetup,
    account: Account,
    today: LocalDate,
    zone: ZoneId,
    onAction: (AccountAction) -> Unit,
) {
    when (setup) {
        CloudSetup.Done, is CloudSetup.Restored -> Unit
        is CloudSetup.ChooseSource -> AlertDialog(
            onDismissRequest = { onAction(AccountAction.DismissSetup) },
            title = { Text(stringResource(R.string.account_setup_choose_title)) },
            text = {
                val day = backupDayText(setup.backup.savedAt, today, zone)
                Text(
                    if (setup.phone.isEmpty) {
                        stringResource(R.string.account_setup_choose_body_empty_phone, day, countsText(setup.backup.counts))
                    } else {
                        stringResource(R.string.account_setup_choose_body, day, countsText(setup.backup.counts), countsText(setup.phone))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(AccountAction.Choose(CloudSetupChoice.USE_BACKUP)) }) {
                    Text(stringResource(R.string.account_setup_use_backup))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(AccountAction.Choose(CloudSetupChoice.KEEP_PHONE)) }) {
                    Text(stringResource(if (setup.phone.isEmpty) R.string.account_setup_start_fresh else R.string.account_setup_keep_phone))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        is CloudSetup.OtherAccountData -> AlertDialog(
            onDismissRequest = { onAction(AccountAction.DismissSetup) },
            title = { Text(stringResource(R.string.account_setup_other_title)) },
            text = {
                val who = account.email ?: account.displayName.orEmpty()
                val backup = setup.backup
                Text(
                    if (backup != null) {
                        stringResource(R.string.account_setup_other_body, countsText(setup.phone), who, backupDayText(backup.savedAt, today, zone))
                    } else {
                        stringResource(R.string.account_setup_other_body_no_backup, countsText(setup.phone), who)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(AccountAction.Choose(CloudSetupChoice.USE_BACKUP)) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.account_setup_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(AccountAction.ConfirmSignOut) }) {
                    Text(stringResource(R.string.account_sign_out))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** "142 workouts · 6 routines · 31 weigh-ins", or "nothing". */
@Composable
private fun countsText(counts: DataCounts): String =
    if (counts.isEmpty) stringResource(R.string.account_nothing) else LocalResources.current.dataCountsText(counts)

private fun android.content.res.Resources.countsText(counts: DataCounts): String =
    if (counts.isEmpty) getString(R.string.account_nothing) else dataCountsText(counts)

@Composable
private fun AccountSkeleton(modifier: Modifier = Modifier) {
    SkeletonContainer(contentDescription = stringResource(R.string.account_loading), modifier = modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SkeletonBlock(Modifier.padding(bottom = Spacing.md).fillMaxWidth(0.4f).height(Spacing.xl))
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonPanelHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.padding(top = Spacing.lg, bottom = Spacing.xxs).fillMaxWidth(0.3f).height(Spacing.md))
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonPanelHeight), MaterialTheme.shapes.large)
            SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonRowHeight), MaterialTheme.shapes.large)
        }
    }
}

private val SecondaryActionHeight = 56.dp
private val SkeletonPanelHeight = 120.dp
private val SkeletonRowHeight = 64.dp
private const val CONTENT_FADE_MILLIS = 200

private val previewToday = LocalDate.of(2026, 9, 26)
private val previewAccount = Account(id = "uid", email = "sam@example.com", displayName = null, hasPassword = true)

@ThemePreviews
@Composable
private fun AccountScreenPreview() {
    LiftBookTheme {
        AccountScreen(
            state = AccountUiState(
                isLoading = false,
                account = previewAccount,
                isOwner = true,
                backup = CloudBackupStatus(lastBackupAt = Instant.parse("2026-09-23T19:00:00Z"), isPending = true),
                cloud = CloudBackupInfo(Instant.parse("2026-09-23T19:00:00Z"), DataCounts(workouts = 140, routines = 6, weighIns = 30)),
                hasCheckedCloud = true,
                phone = DataCounts(workouts = 142, routines = 6, weighIns = 31),
                today = previewToday,
                zone = ZoneOffset.UTC,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun AccountScreenFailedBackupPreview() {
    LiftBookTheme {
        AccountScreen(
            state = AccountUiState(
                isLoading = false,
                account = previewAccount.copy(hasPassword = false, displayName = "Sam"),
                isOwner = true,
                backup = CloudBackupStatus(lastBackupAt = Instant.parse("2026-09-12T08:00:00Z"), isPending = true, lastFailure = AccountProblem.OFFLINE),
                hasCheckedCloud = true,
                today = previewToday,
                zone = ZoneOffset.UTC,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun AccountScreenSignedOutPreview() {
    LiftBookTheme {
        AccountScreen(
            state = AccountUiState(isLoading = false, today = previewToday, zone = ZoneOffset.UTC),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@ThemePreviews
@Composable
private fun AccountScreenUnavailablePreview() {
    LiftBookTheme {
        AccountScreen(
            state = AccountUiState(isLoading = false, isAvailable = false, today = previewToday, zone = ZoneOffset.UTC),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
