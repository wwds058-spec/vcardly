package com.yasin.vcardly.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.notifications.ReminderPermissions
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.security.AuthAvailability
import com.yasin.vcardly.core.security.AuthResult
import com.yasin.vcardly.core.security.AutoLockOptions
import com.yasin.vcardly.presentation.common.LocalAuthGate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.BuildConfig
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.model.ThemeMode

@Composable
fun SettingsScreen(
    onOpenOrganize: () -> Unit,
    onOpenMyCard: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.nav_settings))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.md),
        ) {
            SectionHeader(stringResource(R.string.settings_theme))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ThemeOption(
                        label = stringResource(mode.labelRes()),
                        selected = mode == themeMode,
                        onSelect = { viewModel.setThemeMode(mode) },
                    )
                }
            }

            SecuritySection(viewModel, onOpenPrivacy)

            ReminderStatusSection()

            SectionHeader(stringResource(R.string.settings_data))
            SettingsLink(stringResource(R.string.settings_my_card), onOpenMyCard)
            SettingsLink(stringResource(R.string.settings_organize), onOpenOrganize)
            SettingsLink(stringResource(R.string.settings_transfer), onOpenTransfer)
            SettingsLink(stringResource(R.string.reports_title), onOpenReports)
            SettingsLink(stringResource(R.string.backup_title), onOpenBackup)

            SectionHeader(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.sm),
            )
            Text(
                stringResource(R.string.settings_offline_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The whole row is the touch target and is announced as one radio button. */
@Composable
private fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.spacing.minTouchTarget)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = MaterialTheme.spacing.md))
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** Shows whether reminders can actually reach the user, with a one-tap route to fix each blocker. */
@Composable
private fun ReminderStatusSection() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // Re-check when returning from the system settings screens.
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val notificationsOk = remember(tick) { ReminderPermissions.notificationsEnabled(context) }
    val exactOk = remember(tick) { ReminderPermissions.canScheduleExact(context) }

    SectionHeader(stringResource(R.string.settings_reminders))
    StatusRow(
        text = stringResource(if (notificationsOk) R.string.settings_notifications_on else R.string.settings_notifications_off),
        actionText = if (notificationsOk) null else stringResource(R.string.settings_open_notification_settings),
        onAction = { context.startActivity(ReminderPermissions.notificationSettingsIntent(context)) },
    )
    val exactIntent = ReminderPermissions.exactAlarmSettingsIntent(context)
    StatusRow(
        text = stringResource(if (exactOk) R.string.settings_exact_on else R.string.settings_exact_off),
        actionText = if (exactOk || exactIntent == null) null else stringResource(R.string.settings_allow_exact),
        onAction = { exactIntent?.let(context::startActivity) },
    )
}

@Composable
private fun StatusRow(text: String, actionText: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.xs)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (actionText != null) VCardlyTextButton(actionText, onClick = onAction)
    }
}

@Composable
private fun SettingsLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.sm),
    )
}

@Composable
private fun SecuritySection(viewModel: SettingsViewModel, onOpenPrivacy: () -> Unit) {
    val security by viewModel.security.collectAsStateWithLifecycle()
    val gate = LocalAuthGate.current
    val confirmTitle = stringResource(R.string.settings_lock_confirm)
    var unavailable by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.settings_security))
    SwitchRow(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_hint), security.appLockEnabled) { on ->
        if (on) {
            when (gate.availability()) {
                AuthAvailability.NotSetUp, AuthAvailability.Temporary -> unavailable = true
                // Prove the user can actually pass the lock before it is switched on, so they cannot lock themselves out.
                AuthAvailability.Available -> gate.authenticate(confirmTitle) { if (it == AuthResult.Success) viewModel.enableAppLock() }
            }
        } else {
            gate.authenticate(confirmTitle) { if (it == AuthResult.Success) viewModel.disableAppLock() }
        }
    }
    if (security.appLockEnabled) {
        Text(stringResource(R.string.settings_auto_lock), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = MaterialTheme.spacing.sm))
        Column(Modifier.selectableGroup()) {
            AutoLockOptions.seconds.forEach { seconds ->
                ThemeOption(
                    label = autoLockLabel(seconds),
                    selected = seconds == security.autoLockSeconds,
                    onSelect = { viewModel.setAutoLockSeconds(seconds) },
                )
            }
        }
    }
    SwitchRow(stringResource(R.string.settings_secure_screen), stringResource(R.string.settings_secure_screen_hint), security.secureScreen, viewModel::setSecureScreen)
    SettingsLink(stringResource(R.string.privacy_title), onOpenPrivacy)
    SettingsLink(stringResource(R.string.settings_erase)) { erase = true }

    if (unavailable) {
        ConfirmDialog(
            title = stringResource(R.string.settings_lock_unavailable_title),
            message = stringResource(R.string.settings_lock_unavailable_message),
            confirmText = stringResource(R.string.common_ok),
            onConfirm = { unavailable = false },
            onDismiss = { unavailable = false },
        )
    }
    if (erase) {
        ConfirmDialog(
            title = stringResource(R.string.settings_erase_title),
            message = stringResource(R.string.settings_erase_message),
            confirmText = stringResource(R.string.settings_erase_confirm),
            onConfirm = { erase = false; viewModel.eraseAllData { } },
            onDismiss = { erase = false },
        )
    }
}

@Composable
private fun autoLockLabel(seconds: Int): String = when {
    seconds < 60 -> pluralStringResource(R.plurals.settings_auto_lock_seconds, seconds, seconds)
    else -> pluralStringResource(R.plurals.settings_auto_lock_minutes, seconds / 60, seconds / 60)
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = MaterialTheme.spacing.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
