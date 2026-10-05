package com.yasin.vcardly.presentation.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.backup.BackupFailure
import com.yasin.vcardly.core.backup.RestoreMode
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTag
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.CardShape
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.backup.BackupFormat
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

data class BackupActions(
    val onNavigateUp: () -> Unit = {},
    /** Opens the "save as" picker; the password (or null) is captured by the screen and passed on once a file is chosen. */
    val onCreate: (password: CharArray?) -> Unit = {},
    val onChooseRestoreFile: () -> Unit = {},
    val onSubmitPassword: (CharArray) -> Unit = {},
    val onRestore: (RestoreMode) -> Unit = {},
    val onResetRestore: () -> Unit = {},
)

@Composable
fun BackupScreen(onNavigateUp: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The password lives only in memory until the user has picked a file, then is handed over and wiped.
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val pw = pendingPassword
        pendingPassword = null
        if (uri != null) viewModel.createBackup(uri, pw) else pw?.fill('\u0000')
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::chooseRestoreFile) }
    val suggestedName = stringResource(R.string.backup_file_name, LocalDate.now().toString(), BackupFormat.EXTENSION)

    BackupContent(
        state = state,
        minPasswordLength = viewModel.minPasswordLength,
        actions = BackupActions(
            onNavigateUp = onNavigateUp,
            onCreate = { pw -> pendingPassword = pw; saveLauncher.launch(suggestedName) },
            onChooseRestoreFile = { viewModel.resetRestore(); openLauncher.launch(arrayOf("*/*")) },
            onSubmitPassword = viewModel::submitPassword,
            onRestore = viewModel::restore,
            onResetRestore = viewModel::resetRestore,
        ),
    )
}

/** Stateless backup screen (used directly by UI tests). Every message reflects what actually happened. */
@Composable
fun BackupContent(state: BackupUiState, minPasswordLength: Int, actions: BackupActions) {
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.backup_title), onNavigateUp = actions.onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = MaterialTheme.spacing.screen).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusHeader(state.lastBackupAt)
            CreateCard(state.create, minPasswordLength, actions.onCreate)
            RestoreCard(state.restore, actions)
            DriveCard(state.driveConfigured)
        }
    }
}

@Composable
private fun StatusHeader(lastBackupAt: Long?) {
    val colors = MaterialTheme.vcColors
    val dateTime = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val tone = if (lastBackupAt != null) colors.mint else colors.orange
    VCardlyCard(Modifier.fillMaxWidth(), color = tone.container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (lastBackupAt != null) Icons.Rounded.Shield else Icons.Rounded.Warning, tone, size = 48.dp, iconSize = 24.dp, circle = true, solid = true)
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    stringResource(if (lastBackupAt != null) R.string.backup_status_protected else R.string.backup_status_none),
                    style = MaterialTheme.typography.titleMedium, color = tone.content,
                )
                Text(
                    lastBackupAt?.let { stringResource(R.string.backup_last, dateTime.format(Date(it))) } ?: stringResource(R.string.backup_never),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CreateCard(create: CreateState, minPasswordLength: Int, onCreate: (CharArray?) -> Unit) {
    val colors = MaterialTheme.vcColors
    var protect by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val passwordOk = !protect || (password.length >= minPasswordLength && password == confirm)
    val working = create == CreateState.Working

    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Save, colors.blue, size = 40.dp, circle = true)
            Text(stringResource(R.string.backup_create), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
        }
        Text(stringResource(R.string.backup_create_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp).toggleable(value = protect, role = Role.Switch) { protect = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(if (protect) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (protect) colors.mint else colors.orange, size = 36.dp, iconSize = 18.dp, circle = true)
            Text(stringResource(R.string.backup_protect), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleSmall)
            Switch(checked = protect, onCheckedChange = null)
        }
        AnimatedVisibility(protect) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VCardlyTextField(
                    password, { password = it }, stringResource(R.string.backup_password),
                    errorText = if (password.isNotEmpty() && password.length < minPasswordLength) stringResource(R.string.backup_password_short, minPasswordLength) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                VCardlyTextField(
                    confirm, { confirm = it }, stringResource(R.string.backup_password_confirm),
                    errorText = if (confirm.isNotEmpty() && confirm != password) stringResource(R.string.backup_password_mismatch) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }
        VCardlyNotice(
            stringResource(if (protect) R.string.backup_password_warning else R.string.backup_unprotected_warning),
            if (protect) colors.blue else colors.orange,
            if (protect) Icons.Rounded.Lock else Icons.Rounded.Warning,
            Modifier.padding(top = 12.dp),
        )
        VCardlyPrimaryButton(
            stringResource(if (working) R.string.backup_creating else R.string.backup_create_button),
            onClick = {
                onCreate(if (protect) password.toCharArray() else null)
                password = ""; confirm = ""
            },
            enabled = passwordOk && !working,
            loading = working,
            leadingIcon = Icons.Rounded.Save,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when (create) {
                CreateState.Working -> Text(stringResource(R.string.backup_creating), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                is CreateState.Done -> VCardlyNotice(
                    pluralStringResource(R.plurals.backup_created, create.counts.contacts, create.counts.contacts, create.counts.followUps, create.counts.images),
                    colors.mint, Icons.Rounded.CheckCircle, Modifier.padding(top = 12.dp),
                )
                is CreateState.Failed -> VCardlyNotice(
                    stringResource(R.string.backup_failed_title) + " " + stringResource(create.reason.messageRes()),
                    colors.rose, Icons.Rounded.ErrorOutline, Modifier.padding(top = 12.dp),
                )
                CreateState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun RestoreCard(restore: RestoreState, actions: BackupActions) {
    val colors = MaterialTheme.vcColors
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }
    val dateTime = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Restore, colors.lavender, size = 40.dp, circle = true)
            Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (restore) {
                RestoreState.Idle -> {
                    Text(stringResource(R.string.backup_restore_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VCardlySecondaryButton(stringResource(R.string.backup_choose_file), onClick = actions.onChooseRestoreFile, leadingIcon = Icons.Rounded.FileOpen, modifier = Modifier.fillMaxWidth())
                }
                RestoreState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(stringResource(R.string.backup_validating), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                }
                is RestoreState.NeedsPassword -> PasswordDialog(wrong = restore.wrong, onSubmit = actions.onSubmitPassword, onCancel = actions.onResetRestore)
                is RestoreState.Ready -> {
                    val m = restore.manifest
                    VCardlyNotice(
                        stringResource(R.string.backup_file_from, dateTime.format(Date(m.createdAt))) + "\n" +
                            pluralStringResource(R.plurals.backup_contents, m.counts.contacts, m.counts.contacts, m.counts.followUps, m.counts.images),
                        colors.blue, Icons.Rounded.CloudDone,
                    )
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeOption(R.string.backup_mode_merge, R.string.backup_mode_merge_hint, mode == RestoreMode.MERGE) { mode = RestoreMode.MERGE }
                        ModeOption(R.string.backup_mode_replace, R.string.backup_mode_replace_hint, mode == RestoreMode.REPLACE) { mode = RestoreMode.REPLACE }
                    }
                    VCardlyPrimaryButton(
                        stringResource(R.string.backup_restore_button),
                        onClick = { if (mode == RestoreMode.REPLACE) confirmReplace = true else actions.onRestore(mode) },
                        leadingIcon = Icons.Rounded.Restore,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VCardlyTextButton(stringResource(R.string.common_cancel), onClick = actions.onResetRestore, modifier = Modifier.fillMaxWidth())
                }
                is RestoreState.Done -> {
                    VCardlyNotice(pluralStringResource(R.plurals.backup_restored, restore.summary.contactsAdded, restore.summary.contactsAdded), colors.mint, Icons.Rounded.CheckCircle)
                    if (restore.summary.duplicatesSkipped > 0) {
                        Text(stringResource(R.string.backup_skipped_duplicates, restore.summary.duplicatesSkipped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VCardlyTextButton(stringResource(R.string.backup_restore_another), onClick = actions.onResetRestore)
                }
                is RestoreState.Failed -> {
                    VCardlyNotice(stringResource(R.string.backup_restore_failed_title) + " " + stringResource(restore.reason.messageRes()), colors.rose, Icons.Rounded.ErrorOutline)
                    VCardlySecondaryButton(stringResource(R.string.backup_choose_file), onClick = actions.onChooseRestoreFile, leadingIcon = Icons.Rounded.FileOpen, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.backup_replace_confirm_title),
            message = stringResource(R.string.backup_replace_confirm_message),
            confirmText = stringResource(R.string.backup_replace_confirm),
            destructive = true,
            onConfirm = { confirmReplace = false; actions.onRestore(RestoreMode.REPLACE) },
            onDismiss = { confirmReplace = false },
        )
    }
}

/** Google Drive is prepared behind CloudBackupProvider but honestly reports when it is not set up in this build. */
@Composable
private fun DriveCard(configured: Boolean) {
    val colors = MaterialTheme.vcColors
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (configured) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, if (configured) colors.mint else colors.navy, size = 40.dp, circle = true)
            Text(stringResource(R.string.backup_drive_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
            VCardlyTag(stringResource(if (configured) R.string.backup_drive_ready else R.string.backup_drive_not_configured), if (configured) colors.mint else colors.orange)
        }
        Text(stringResource(R.string.backup_drive_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ModeOption(title: Int, hint: Int, selected: Boolean, onSelect: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(if (selected) 2.dp else 1.dp, border, CardShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (CharArray) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.backup_password_prompt)) },
        text = {
            VCardlyTextField(
                text, { text = it }, stringResource(R.string.backup_password),
                errorText = if (wrong) stringResource(R.string.backup_error_wrong_password) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        confirmButton = { VCardlyTextButton(stringResource(R.string.common_ok), onClick = { onSubmit(text.toCharArray()); text = "" }, enabled = text.isNotEmpty()) },
        dismissButton = { VCardlyTextButton(stringResource(R.string.common_cancel), onClick = onCancel) },
    )
}

private fun BackupFailure.messageRes(): Int = when (this) {
    BackupFailure.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
    BackupFailure.NEWER_VERSION -> R.string.backup_error_newer
    BackupFailure.NEEDS_PASSWORD, BackupFailure.WRONG_PASSWORD -> R.string.backup_error_wrong_password
    BackupFailure.CORRUPT -> R.string.backup_error_corrupt
    BackupFailure.TOO_LARGE -> R.string.backup_error_too_large
    BackupFailure.IO -> R.string.backup_error_io
}
