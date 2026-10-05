package com.yasin.vcardly.presentation.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.backup.BackupFailure
import com.yasin.vcardly.core.backup.RestoreMode
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.backup.BackupManifest
import com.yasin.vcardly.domain.backup.BackupFormat
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import androidx.compose.ui.unit.dp

@Composable
fun BackupScreen(onNavigateUp: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateTime = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    // Password fields live only in this composable's memory and are cleared after use.
    var protect by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val passwordOk = !protect || (password.length >= viewModel.minPasswordLength && password == confirm)

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            viewModel.createBackup(uri, if (protect) password.toCharArray() else null)
            password = ""; confirm = ""
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::chooseRestoreFile) }
    val suggestedName = stringResource(R.string.backup_file_name, LocalDate.now().toString(), BackupFormat.EXTENSION)

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.backup_title), onNavigateUp = onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            // ------------------------------------------------ create
            SectionHeader(stringResource(R.string.backup_create))
            Text(stringResource(R.string.backup_create_description), style = MaterialTheme.typography.bodyMedium)
            Text(
                state.lastBackupAt?.let { stringResource(R.string.backup_last, dateTime.format(Date(it))) } ?: stringResource(R.string.backup_never),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val protectLabel = stringResource(R.string.backup_protect)
            Row(
                Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.minTouchTarget)
                    .toggleable(value = protect, role = Role.Switch) { protect = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(protectLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = protect, onCheckedChange = null)
            }
            if (protect) {
                PasswordField(password, { password = it }, stringResource(R.string.backup_password), error = password.isNotEmpty() && password.length < viewModel.minPasswordLength,
                    errorText = stringResource(R.string.backup_password_short, viewModel.minPasswordLength))
                PasswordField(confirm, { confirm = it }, stringResource(R.string.backup_password_confirm), error = confirm.isNotEmpty() && confirm != password,
                    errorText = stringResource(R.string.backup_password_mismatch))
                Text(stringResource(R.string.backup_password_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                Text(stringResource(R.string.backup_unprotected_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            VCardlyPrimaryButton(
                stringResource(R.string.backup_create_button),
                onClick = { saveLauncher.launch(suggestedName) },
                enabled = passwordOk && state.create != CreateState.Working,
                modifier = Modifier.fillMaxWidth(),
            )
            when (val c = state.create) {
                CreateState.Working -> CircularProgressIndicator()
                is CreateState.Done -> Text(
                    pluralStringResource(R.plurals.backup_created, c.counts.contacts, c.counts.contacts, c.counts.followUps, c.counts.images),
                    color = MaterialTheme.colorScheme.primary,
                )
                is CreateState.Failed -> Text(stringResource(c.reason.messageRes()), color = MaterialTheme.colorScheme.error)
                CreateState.Idle -> Unit
            }

            // ------------------------------------------------ restore
            SectionHeader(stringResource(R.string.backup_restore))
            RestoreSection(state.restore, viewModel, onChoose = { openLauncher.launch(arrayOf("*/*")) })

            // ------------------------------------------------ drive
            SectionHeader(stringResource(R.string.backup_drive_title))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    Text(
                        stringResource(if (state.driveConfigured) R.string.backup_drive_ready else R.string.backup_drive_not_configured),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.backup_drive_explanation), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, error: Boolean, errorText: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = error, supportingText = if (error) ({ Text(errorText) }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RestoreSection(restore: RestoreState, viewModel: BackupViewModel, onChoose: () -> Unit) {
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }
    val dateTime = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    when (restore) {
        RestoreState.Idle -> {
            Text(stringResource(R.string.backup_restore_description), style = MaterialTheme.typography.bodyMedium)
            VCardlySecondaryButton(stringResource(R.string.backup_choose_file), onClick = onChoose, modifier = Modifier.fillMaxWidth())
        }
        RestoreState.Working -> CircularProgressIndicator()
        is RestoreState.NeedsPassword -> PasswordDialog(wrong = restore.wrong, onSubmit = viewModel::submitPassword, onCancel = viewModel::resetRestore)
        is RestoreState.Ready -> {
            val m: BackupManifest = restore.manifest
            Text(stringResource(R.string.backup_file_from, dateTime.format(Date(m.createdAt))), style = MaterialTheme.typography.bodyLarge)
            Text(
                pluralStringResource(R.plurals.backup_contents, m.counts.contacts, m.counts.contacts, m.counts.followUps, m.counts.images),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                ModeOption(R.string.backup_mode_merge, R.string.backup_mode_merge_hint, mode == RestoreMode.MERGE) { mode = RestoreMode.MERGE }
                ModeOption(R.string.backup_mode_replace, R.string.backup_mode_replace_hint, mode == RestoreMode.REPLACE) { mode = RestoreMode.REPLACE }
            }
            VCardlyPrimaryButton(
                stringResource(R.string.backup_restore_button),
                onClick = { if (mode == RestoreMode.REPLACE) confirmReplace = true else viewModel.restore(mode) },
                modifier = Modifier.fillMaxWidth(),
            )
            VCardlyTextButton(stringResource(R.string.common_cancel), onClick = viewModel::resetRestore)
            if (confirmReplace) {
                ConfirmDialog(
                    title = stringResource(R.string.backup_replace_confirm_title),
                    message = stringResource(R.string.backup_replace_confirm_message),
                    confirmText = stringResource(R.string.backup_replace_confirm),
                    onConfirm = { confirmReplace = false; viewModel.restore(RestoreMode.REPLACE) },
                    onDismiss = { confirmReplace = false },
                )
            }
        }
        is RestoreState.Done -> {
            Text(
                pluralStringResource(R.plurals.backup_restored, restore.summary.contactsAdded, restore.summary.contactsAdded),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge,
            )
            if (restore.summary.duplicatesSkipped > 0) {
                Text(stringResource(R.string.backup_skipped_duplicates, restore.summary.duplicatesSkipped), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VCardlyTextButton(stringResource(R.string.backup_restore_another), onClick = viewModel::resetRestore)
        }
        is RestoreState.Failed -> {
            Text(stringResource(restore.reason.messageRes()), color = MaterialTheme.colorScheme.error)
            VCardlySecondaryButton(stringResource(R.string.backup_choose_file), onClick = { viewModel.resetRestore(); onChoose() })
        }
    }
}

@Composable
private fun ModeOption(title: Int, hint: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = MaterialTheme.spacing.md)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (CharArray) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_password_prompt)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true, label = { Text(stringResource(R.string.backup_password)) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = wrong, supportingText = if (wrong) ({ Text(stringResource(R.string.backup_error_wrong_password)) }) else null,
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
