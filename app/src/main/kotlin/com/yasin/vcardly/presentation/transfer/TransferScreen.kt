package com.yasin.vcardly.presentation.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTonalButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.vcard.ImportCandidate
import com.yasin.vcardly.domain.vcard.ImportStatus

class TransferActions(
    val onNavigateUp: () -> Unit = {},
    val onChooseFile: () -> Unit = {},
    val onToggle: (Int) -> Unit = {},
    val onImportSelected: () -> Unit = {},
    val onResetImport: () -> Unit = {},
    val onExportAll: () -> Unit = {},
)

@Composable
fun TransferScreen(onNavigateUp: () -> Unit, viewModel: TransferViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // System file pickers: no storage permission needed, and the user sees exactly which file is read or written.
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::readFile) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri -> uri?.let(viewModel::exportAll) }
    val exportFileName = stringResource(R.string.transfer_export_file_name)

    TransferContent(
        state,
        TransferActions(
            onNavigateUp = onNavigateUp,
            onChooseFile = { viewModel.resetImport(); openLauncher.launch(arrayOf("*/*")) },
            onToggle = viewModel::toggle,
            onImportSelected = viewModel::importSelected,
            onResetImport = viewModel::resetImport,
            onExportAll = { saveLauncher.launch(exportFileName) },
        ),
    )
}

/** Stateless import/export screen (used directly by UI tests). */
@Composable
fun TransferContent(state: TransferUiState, actions: TransferActions) {
    var confirmExport by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.transfer_title), onNavigateUp = actions.onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.screen).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ImportCard(state.import, actions)
            ExportCard(state.export, onExport = { confirmExport = true })
        }
    }
    if (confirmExport) {
        ConfirmDialog(
            title = stringResource(R.string.transfer_export_confirm_title),
            message = stringResource(R.string.transfer_export_confirm_message),
            confirmText = stringResource(R.string.transfer_export_all),
            onConfirm = { confirmExport = false; actions.onExportAll() },
            onDismiss = { confirmExport = false },
        )
    }
}

@Composable
private fun CardHeader(icon: ImageVector, tone: Tone, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        IconBadge(icon, tone, size = 40.dp, circle = true)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportCard(import: ImportState, actions: TransferActions) {
    val colors = MaterialTheme.vcColors
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        CardHeader(Icons.Rounded.Download, colors.blue, stringResource(R.string.transfer_import), stringResource(R.string.transfer_import_description))
        Column(Modifier.padding(top = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (import) {
                ImportState.Idle -> ChooseButton(actions.onChooseFile)
                ImportState.Reading, ImportState.Importing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(
                        stringResource(if (import == ImportState.Reading) R.string.transfer_reading else R.string.transfer_importing),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp),
                    )
                }
                is ImportState.Failed -> {
                    VCardlyNotice(stringResource(import.reason.messageRes()), colors.rose, Icons.Rounded.ErrorOutline)
                    ChooseButton(actions.onChooseFile)
                }
                is ImportState.Done -> {
                    VCardlyNotice(pluralStringResource(R.plurals.transfer_imported, import.imported, import.imported), colors.mint, Icons.Rounded.CheckCircle)
                    if (import.duplicates > 0) Text(stringResource(R.string.transfer_skipped_duplicates, import.duplicates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (import.unusable > 0) Text(stringResource(R.string.transfer_skipped_unusable, import.unusable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VCardlySecondaryButton(stringResource(R.string.transfer_import_another), onClick = actions.onChooseFile, leadingIcon = Icons.Rounded.FileOpen, modifier = Modifier.fillMaxWidth())
                }
                is ImportState.Preview -> {
                    Text(stringResource(R.string.transfer_preview_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        import.entries.forEach { e -> EntryRow(e, checked = e.index in import.selected, onToggle = { actions.onToggle(e.index) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VCardlySecondaryButton(stringResource(R.string.common_cancel), onClick = actions.onResetImport, modifier = Modifier.weight(1f))
                        VCardlyPrimaryButton(
                            text = stringResource(R.string.transfer_import_selected, import.selected.size),
                            onClick = actions.onImportSelected,
                            enabled = import.selected.isNotEmpty(),
                            leadingIcon = Icons.Rounded.Download,
                            modifier = Modifier.weight(1.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseButton(onClick: () -> Unit) {
    VCardlyTonalButton(stringResource(R.string.transfer_choose_file), onClick = onClick, leadingIcon = Icons.Rounded.FileOpen, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ExportCard(export: ExportState, onExport: () -> Unit) {
    val colors = MaterialTheme.vcColors
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        CardHeader(Icons.Rounded.Upload, colors.lavender, stringResource(R.string.transfer_export), stringResource(R.string.transfer_export_description))
        Column(Modifier.padding(top = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (export) {
                is ExportState.Done -> VCardlyNotice(pluralStringResource(R.plurals.transfer_exported, export.count, export.count), colors.mint, Icons.Rounded.CheckCircle)
                ExportState.Failed -> VCardlyNotice(stringResource(R.string.transfer_export_failed), colors.rose, Icons.Rounded.ErrorOutline)
                ExportState.Working, ExportState.Idle -> Unit
            }
            VCardlyNotice(stringResource(R.string.transfer_export_privacy), colors.orange, Icons.Rounded.Lock)
            VCardlyTonalButton(
                stringResource(R.string.transfer_export_all),
                onClick = onExport,
                enabled = export != ExportState.Working,
                leadingIcon = Icons.Rounded.Upload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EntryRow(entry: ImportCandidate, checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.vcColors
    val usable = entry.status == ImportStatus.NEW
    val name = entry.contact?.fullName ?: stringResource(R.string.transfer_unnamed)
    val detail = listOfNotNull(entry.contact?.company?.takeIf { it.isNotBlank() }, entry.contact?.email?.takeIf { it.isNotBlank() }, entry.contact?.phone?.takeIf { it.isNotBlank() })
        .firstOrNull().orEmpty()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = usable, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = usable)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = if (usable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            when (entry.status) {
                ImportStatus.DUPLICATE -> Text(stringResource(R.string.transfer_status_duplicate), style = MaterialTheme.typography.bodySmall, color = colors.orange.content)
                ImportStatus.UNUSABLE -> Text(stringResource(R.string.transfer_status_unusable), style = MaterialTheme.typography.bodySmall, color = colors.rose.content)
                ImportStatus.NEW -> if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun ImportFailure.messageRes(): Int = when (this) {
    ImportFailure.UNREADABLE -> R.string.transfer_error_unreadable
    ImportFailure.TOO_LARGE -> R.string.transfer_error_too_large
    ImportFailure.NO_CONTACTS -> R.string.transfer_error_no_contacts
}
