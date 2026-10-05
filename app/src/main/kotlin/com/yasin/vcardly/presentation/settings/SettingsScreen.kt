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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
fun SettingsScreen(onOpenOrganize: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
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

            ReminderStatusSection()

            SectionHeader(stringResource(R.string.settings_data))
            Text(
                stringResource(R.string.settings_organize),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MaterialTheme.spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClick = onOpenOrganize)
                    .padding(vertical = MaterialTheme.spacing.sm),
            )

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
