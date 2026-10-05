package com.yasin.vcardly.presentation.transfer

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.vcard.ImportCandidate
import com.yasin.vcardly.domain.vcard.ImportStatus

@Composable
fun TransferScreen(onNavigateUp: () -> Unit, viewModel: TransferViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmExport by remember { mutableStateOf(false) }

    // System file pickers: no storage permission needed, and the user sees exactly which file is read/written.
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::readFile) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri -> uri?.let(viewModel::exportAll) }
    val exportFileName = stringResource(R.string.transfer_export_file_name)
    val import = state.import

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.transfer_title), onNavigateUp = onNavigateUp)
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            item { VCardlySectionHeader(stringResource(R.string.transfer_import)) }
            when (import) {
                ImportState.Idle -> item { ImportIntro { openLauncher.launch(arrayOf("*/*")) } }
                ImportState.Reading, ImportState.Importing -> item { CircularProgressIndicator() }
                is ImportState.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        Text(stringResource(import.reason.messageRes()), color = MaterialTheme.colorScheme.error)
                        VCardlySecondaryButton(stringResource(R.string.transfer_choose_file), onClick = { openLauncher.launch(arrayOf("*/*")) })
                    }
                }
                is ImportState.Done -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        Text(pluralStringResource(R.plurals.transfer_imported, import.imported, import.imported), style = MaterialTheme.typography.bodyLarge)
                        if (import.duplicates > 0) Text(stringResource(R.string.transfer_skipped_duplicates, import.duplicates), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (import.unusable > 0) Text(stringResource(R.string.transfer_skipped_unusable, import.unusable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        VCardlyTextButton(stringResource(R.string.transfer_import_another), onClick = viewModel::resetImport)
                    }
                }
                is ImportState.Preview -> {
                    item {
                        Text(stringResource(R.string.transfer_preview_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(import.entries, key = { it.index }) { e ->
                        EntryRow(e, checked = e.index in import.selected, onToggle = { viewModel.toggle(e.index) })
                    }
                    item {
                        Column {
                            VCardlyPrimaryButton(
                                text = stringResource(R.string.transfer_import_selected, import.selected.size),
                                onClick = viewModel::importSelected,
                                enabled = import.selected.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            VCardlyTextButton(stringResource(R.string.common_cancel), onClick = viewModel::resetImport)
                        }
                    }
                }
            }

            item { VCardlySectionHeader(stringResource(R.string.transfer_export)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    Text(stringResource(R.string.transfer_export_description), style = MaterialTheme.typography.bodyMedium)
                    when (val e = state.export) {
                        ExportState.Working -> CircularProgressIndicator()
                        is ExportState.Done -> Text(pluralStringResource(R.plurals.transfer_exported, e.count, e.count), color = MaterialTheme.colorScheme.primary)
                        ExportState.Failed -> Text(stringResource(R.string.transfer_export_failed), color = MaterialTheme.colorScheme.error)
                        ExportState.Idle -> Unit
                    }
                    VCardlySecondaryButton(
                        stringResource(R.string.transfer_export_all),
                        onClick = { confirmExport = true },
                        enabled = state.export != ExportState.Working,
                    )
                }
            }
        }
    }

    if (confirmExport) {
        ConfirmDialog(
            title = stringResource(R.string.transfer_export_confirm_title),
            message = stringResource(R.string.transfer_export_confirm_message),
            confirmText = stringResource(R.string.transfer_export_all),
            onConfirm = { confirmExport = false; saveLauncher.launch(exportFileName) },
            onDismiss = { confirmExport = false },
        )
    }
}

@Composable
private fun ImportIntro(onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(stringResource(R.string.transfer_import_description), style = MaterialTheme.typography.bodyMedium)
        VCardlySecondaryButton(stringResource(R.string.transfer_choose_file), onClick = onChoose)
    }
}

@Composable
private fun EntryRow(entry: ImportCandidate, checked: Boolean, onToggle: () -> Unit) {
    val usable = entry.status == ImportStatus.NEW
    val name = entry.contact?.fullName ?: stringResource(R.string.transfer_unnamed)
    val detail = listOfNotNull(entry.contact?.company?.takeIf { it.isNotBlank() }, entry.contact?.email?.takeIf { it.isNotBlank() }, entry.contact?.phone?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = usable, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = usable)
        Column(Modifier.weight(1f).padding(start = MaterialTheme.spacing.md)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            when (entry.status) {
                ImportStatus.DUPLICATE -> Text(stringResource(R.string.transfer_status_duplicate), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                ImportStatus.UNUSABLE -> Text(stringResource(R.string.transfer_status_unusable), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                ImportStatus.NEW -> Unit
            }
        }
    }
}

private fun ImportFailure.messageRes(): Int = when (this) {
    ImportFailure.UNREADABLE -> R.string.transfer_error_unreadable
    ImportFailure.TOO_LARGE -> R.string.transfer_error_too_large
    ImportFailure.NO_CONTACTS -> R.string.transfer_error_no_contacts
}
