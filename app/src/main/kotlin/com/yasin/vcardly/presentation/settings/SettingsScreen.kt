package com.yasin.vcardly.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.BuildConfig
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.VCardlyGroup
import com.yasin.vcardly.core.designsystem.component.VCardlyNavigationRow
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlyOverline
import com.yasin.vcardly.core.designsystem.component.VCardlyScreenHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.theme.SheetShape
import com.yasin.vcardly.core.designsystem.theme.TileShape
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.core.notifications.ReminderPermissions
import com.yasin.vcardly.core.security.AuthAvailability
import com.yasin.vcardly.core.security.AuthResult
import com.yasin.vcardly.core.security.AutoLockOptions
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.domain.repository.SecuritySettings
import com.yasin.vcardly.presentation.common.LocalAuthGate

/** Everything Settings shows. */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isPro: Boolean = false,
    val security: SecuritySettings = SecuritySettings(),
    val notificationsOn: Boolean = true,
    val exactAlarmsOn: Boolean = true,
    val canOpenExactSettings: Boolean = false,
    val showAdPrivacy: Boolean = false,
    val version: String = "",
)

data class SettingsActions(
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onAppLock: (Boolean) -> Unit = {},
    val onAutoLock: (Int) -> Unit = {},
    val onSecureScreen: (Boolean) -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    val onOpenExactAlarmSettings: () -> Unit = {},
    val onAdPrivacy: () -> Unit = {},
    val onErase: () -> Unit = {},
    val onOpenOrganize: () -> Unit = {},
    val onOpenMyCard: () -> Unit = {},
    val onOpenTransfer: () -> Unit = {},
    val onOpenReports: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenPro: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    onOpenOrganize: () -> Unit,
    onOpenMyCard: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val security by viewModel.security.collectAsStateWithLifecycle()
    val gate = LocalAuthGate.current
    val confirmTitle = stringResource(R.string.settings_lock_confirm)
    var unavailable by remember { mutableStateOf(false) }

    // Permission state is re-read when the user comes back from the system settings screens.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val notificationsOk = remember(tick) { ReminderPermissions.notificationsEnabled(context) }
    val exactOk = remember(tick) { ReminderPermissions.canScheduleExact(context) }
    val exactIntent = remember(tick) { ReminderPermissions.exactAlarmSettingsIntent(context) }
    val activity = context as? android.app.Activity

    SettingsContent(
        state = SettingsUiState(
            themeMode = themeMode,
            isPro = isPro,
            security = security,
            notificationsOn = notificationsOk,
            exactAlarmsOn = exactOk,
            canOpenExactSettings = exactIntent != null,
            showAdPrivacy = !isPro && viewModel.ads.enabled && viewModel.ads.privacyOptionsRequired() && activity != null,
            version = BuildConfig.VERSION_NAME,
        ),
        actions = SettingsActions(
            onThemeMode = viewModel::setThemeMode,
            onAppLock = { on ->
                if (on) {
                    when (gate.availability()) {
                        AuthAvailability.NotSetUp, AuthAvailability.Temporary -> unavailable = true
                        // Prove the user can actually pass the lock before it is switched on, so they cannot lock themselves out.
                        AuthAvailability.Available -> gate.authenticate(confirmTitle) { if (it == AuthResult.Success) viewModel.enableAppLock() }
                    }
                } else {
                    gate.authenticate(confirmTitle) { if (it == AuthResult.Success) viewModel.disableAppLock() }
                }
            },
            onAutoLock = viewModel::setAutoLockSeconds,
            onSecureScreen = viewModel::setSecureScreen,
            onOpenNotificationSettings = { context.startActivity(ReminderPermissions.notificationSettingsIntent(context)) },
            onOpenExactAlarmSettings = { exactIntent?.let(context::startActivity) },
            onAdPrivacy = { activity?.let { viewModel.ads.showPrivacyOptions(it) } },
            onErase = { viewModel.eraseAllData { } },
            onOpenOrganize = onOpenOrganize,
            onOpenMyCard = onOpenMyCard,
            onOpenTransfer = onOpenTransfer,
            onOpenReports = onOpenReports,
            onOpenBackup = onOpenBackup,
            onOpenPrivacy = onOpenPrivacy,
            onOpenPro = onOpenPro,
        ),
    )

    if (unavailable) {
        ConfirmDialog(
            title = stringResource(R.string.settings_lock_unavailable_title),
            message = stringResource(R.string.settings_lock_unavailable_message),
            confirmText = stringResource(R.string.common_ok),
            onConfirm = { unavailable = false },
            onDismiss = { unavailable = false },
        )
    }
}

private enum class SettingsSheet { APPEARANCE, NOTIFICATIONS, SECURITY }

/** Stateless Settings (used directly by UI tests). Detail options open in bottom sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsUiState, actions: SettingsActions) {
    val colors = MaterialTheme.vcColors
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var erase by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        VCardlyScreenHeader(title = stringResource(R.string.nav_settings))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.screen).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProBanner(state.isPro, actions.onOpenPro)

            Section(stringResource(R.string.settings_section_general)) {
                VCardlyNavigationRow(
                    Icons.Rounded.Palette, colors.blue, stringResource(R.string.settings_appearance),
                    description = stringResource(R.string.settings_appearance_hint, stringResource(state.themeMode.labelRes())),
                    onClick = { sheet = SettingsSheet.APPEARANCE },
                )
                VCardlyNavigationRow(
                    Icons.Rounded.Notifications, colors.orange, stringResource(R.string.settings_notifications),
                    description = stringResource(if (state.notificationsOn) R.string.settings_notifications_hint_on else R.string.settings_notifications_hint_off),
                    onClick = { sheet = SettingsSheet.NOTIFICATIONS },
                    trailing = if (!state.notificationsOn || !state.exactAlarmsOn) { { WarningDot() } } else null,
                )
                VCardlyNavigationRow(
                    Icons.Rounded.Fingerprint, colors.mint, stringResource(R.string.settings_security),
                    description = stringResource(if (state.security.appLockEnabled) R.string.settings_security_hint_on else R.string.settings_security_hint_off),
                    onClick = { sheet = SettingsSheet.SECURITY },
                )
            }

            Section(stringResource(R.string.settings_section_data)) {
                VCardlyNavigationRow(Icons.Rounded.Backup, colors.mint, stringResource(R.string.backup_title), description = stringResource(R.string.settings_backup_hint), onClick = actions.onOpenBackup)
                VCardlyNavigationRow(Icons.Rounded.SwapVert, colors.blue, stringResource(R.string.settings_transfer), description = stringResource(R.string.settings_transfer_hint), onClick = actions.onOpenTransfer)
                VCardlyNavigationRow(Icons.Rounded.BarChart, colors.lavender, stringResource(R.string.reports_title), description = stringResource(R.string.settings_reports_hint), onClick = actions.onOpenReports)
                VCardlyNavigationRow(Icons.Rounded.Label, colors.orange, stringResource(R.string.settings_organize), description = stringResource(R.string.settings_organize_hint), onClick = actions.onOpenOrganize)
                VCardlyNavigationRow(Icons.Rounded.Badge, colors.navy, stringResource(R.string.settings_my_card), description = stringResource(R.string.settings_my_card_hint), onClick = actions.onOpenMyCard)
            }

            Section(stringResource(R.string.settings_section_subscription)) {
                VCardlyNavigationRow(
                    Icons.Rounded.WorkspacePremium, colors.orange,
                    stringResource(if (state.isPro) R.string.pro_settings_active else R.string.pro_settings_upgrade),
                    description = stringResource(if (state.isPro) R.string.settings_pro_hint_active else R.string.settings_pro_hint),
                    onClick = actions.onOpenPro,
                )
                if (state.showAdPrivacy) {
                    VCardlyNavigationRow(Icons.Rounded.Tune, colors.navy, stringResource(R.string.ads_privacy_options), description = stringResource(R.string.settings_ad_privacy_hint), onClick = actions.onAdPrivacy)
                }
            }

            Section(stringResource(R.string.settings_section_help)) {
                VCardlyNavigationRow(Icons.Rounded.Policy, colors.lavender, stringResource(R.string.privacy_title), description = stringResource(R.string.settings_privacy_hint), onClick = actions.onOpenPrivacy)
                VCardlyNavigationRow(Icons.Rounded.DeleteForever, colors.rose, stringResource(R.string.settings_erase), description = stringResource(R.string.settings_erase_hint), onClick = { erase = true })
            }

            Section(stringResource(R.string.settings_about)) {
                VCardlyNavigationRow(
                    Icons.Rounded.Info, colors.navy, stringResource(R.string.app_name),
                    description = stringResource(R.string.settings_version, state.version) + " · " + stringResource(R.string.settings_offline_note),
                    trailing = null,
                )
            }
        }
    }

    when (sheet) {
        SettingsSheet.APPEARANCE -> SettingsSheetHost(stringResource(R.string.settings_appearance), onDismiss = { sheet = null }) {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(stringResource(mode.labelRes()), mode == state.themeMode) { actions.onThemeMode(mode) }
                }
            }
        }
        SettingsSheet.NOTIFICATIONS -> SettingsSheetHost(stringResource(R.string.settings_notifications), onDismiss = { sheet = null }) {
            StatusRow(
                ok = state.notificationsOn,
                text = stringResource(if (state.notificationsOn) R.string.settings_notifications_on else R.string.settings_notifications_off),
                actionText = if (state.notificationsOn) null else stringResource(R.string.settings_open_notification_settings),
                onAction = actions.onOpenNotificationSettings,
            )
            StatusRow(
                ok = state.exactAlarmsOn,
                text = stringResource(if (state.exactAlarmsOn) R.string.settings_exact_on else R.string.settings_exact_off),
                actionText = if (state.exactAlarmsOn || !state.canOpenExactSettings) null else stringResource(R.string.settings_allow_exact),
                onAction = actions.onOpenExactAlarmSettings,
            )
        }
        SettingsSheet.SECURITY -> SettingsSheetHost(stringResource(R.string.settings_security), onDismiss = { sheet = null }) {
            SwitchRow(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_hint), state.security.appLockEnabled, actions.onAppLock)
            if (state.security.appLockEnabled) {
                Text(stringResource(R.string.settings_auto_lock), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Column(Modifier.selectableGroup()) {
                    AutoLockOptions.seconds.forEach { seconds ->
                        RadioRow(autoLockLabel(seconds), seconds == state.security.autoLockSeconds) { actions.onAutoLock(seconds) }
                    }
                }
            }
            SwitchRow(stringResource(R.string.settings_secure_screen), stringResource(R.string.settings_secure_screen_hint), state.security.secureScreen, actions.onSecureScreen)
        }
        null -> Unit
    }

    if (erase) {
        ConfirmDialog(
            title = stringResource(R.string.settings_erase_title),
            message = stringResource(R.string.settings_erase_message),
            confirmText = stringResource(R.string.settings_erase_confirm),
            destructive = true,
            icon = Icons.Rounded.DeleteForever,
            onConfirm = { erase = false; actions.onErase() },
            onDismiss = { erase = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    VCardlyOverline(title, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp))
    VCardlyGroup(content = content)
}

/** Upgrade (or "Pro active") banner at the top of Settings. */
@Composable
private fun ProBanner(isPro: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.vcColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(Brush.linearGradient(if (isPro) colors.gradientIndigo else listOf(colors.gradientPurple.first(), colors.gradientBlue.last())))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(if (isPro) Icons.Rounded.CheckCircle else Icons.Rounded.WorkspacePremium, null, tint = Color.White)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(stringResource(if (isPro) R.string.pro_settings_active else R.string.settings_pro_banner_title), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(stringResource(if (isPro) R.string.pro_active_message else R.string.settings_pro_banner_message), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheetHost(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = SheetShape, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.screen).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun WarningDot() {
    Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.vcColors.rose.accent))
}

/** The whole row is the touch target and is announced as one radio button. */
@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(TileShape).selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun StatusRow(ok: Boolean, text: String, actionText: String?, onAction: () -> Unit) {
    val colors = MaterialTheme.vcColors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        VCardlyNotice(text, if (ok) colors.mint else colors.rose, if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications)
        if (actionText != null) VCardlyTextButton(actionText, onClick = onAction)
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun autoLockLabel(seconds: Int): String = when {
    seconds < 60 -> pluralStringResource(R.plurals.settings_auto_lock_seconds, seconds, seconds)
    else -> pluralStringResource(R.plurals.settings_auto_lock_minutes, seconds / 60, seconds / 60)
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
