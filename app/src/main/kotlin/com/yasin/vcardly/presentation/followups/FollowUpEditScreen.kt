package com.yasin.vcardly.presentation.followups

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.LoadingState
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.notifications.ReminderPermissions
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.model.ReminderOffsets
import com.yasin.vcardly.domain.reminder.DueTime
import com.yasin.vcardly.domain.usecase.FollowUpField
import com.yasin.vcardly.presentation.common.validationMessageRes
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FollowUpEditScreen(
    onNavigateUp: () -> Unit,
    onSaved: () -> Unit,
    viewModel: FollowUpEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form
    val context = LocalContext.current
    var confirmDiscard by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var notificationsOk by remember { mutableStateOf(ReminderPermissions.notificationsEnabled(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = ReminderPermissions.notificationsEnabled(context)
    }

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
    val requestUp = { if (state.isDirty) confirmDiscard = true else onNavigateUp() }
    BackHandler(enabled = state.isDirty, onBack = requestUp)

    val dueDateTime = remember(form.dueAt, state.zone) { java.time.Instant.ofEpochMilli(form.dueAt).atZone(state.zone) }
    val dateTimeFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        VCardlyTopBar(
            title = stringResource(if (state.isNew) R.string.followup_add else R.string.followup_edit),
            onNavigateUp = requestUp,
            actions = {
                VCardlyTextButton(stringResource(R.string.common_save), onClick = viewModel::save, enabled = !state.isLoading && !state.isSaving && !state.notFound)
            },
        )
        when {
            state.isLoading -> LoadingState()
            state.notFound -> EmptyState(Icons.Filled.Warning, stringResource(R.string.followup_not_found_title), stringResource(R.string.followup_not_found_message))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                // Contact: read-only field that opens the picker.
                val contactError = state.errors[FollowUpField.CONTACT]?.let { stringResource(validationMessageRes(it)) }
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { showPicker = true }) {
                    OutlinedTextField(
                        value = form.contactName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.followup_contact)) },
                        placeholder = { Text(stringResource(R.string.followup_contact_pick)) },
                        isError = contactError != null,
                        supportingText = contactError?.let { { Text(it) } },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = if (contactError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledSupportingTextColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                VCardlyTextField(
                    value = form.title,
                    onValueChange = { v -> viewModel.update(FollowUpField.TITLE) { it.copy(title = v) } },
                    label = stringResource(R.string.followup_title_label),
                    errorText = state.errors[FollowUpField.TITLE]?.let { stringResource(validationMessageRes(it)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionHeader(stringResource(R.string.followup_type))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    FollowUpType.entries.forEach { type ->
                        FilterChip(
                            selected = form.type == type,
                            onClick = { viewModel.update { it.copy(type = type) } },
                            label = { Text(stringResource(type.labelRes())) },
                        )
                    }
                }

                SectionHeader(stringResource(R.string.followup_due))
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).heightIn(min = MaterialTheme.spacing.minTouchTarget)) {
                        Text(dateTimeFormat.format(Date(form.dueAt)))
                    }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f).heightIn(min = MaterialTheme.spacing.minTouchTarget)) {
                        Text(timeFormat.format(Date(form.dueAt)))
                    }
                }

                val reminderLabel = stringResource(R.string.followup_reminder)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.minTouchTarget)
                        .toggleable(value = form.reminderEnabled, role = Role.Switch) { on ->
                            viewModel.update { it.copy(reminderEnabled = on) }
                            if (on && Build.VERSION.SDK_INT >= 33 && !notificationsOk) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(reminderLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = form.reminderEnabled, onCheckedChange = null)
                }
                if (form.reminderEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        ReminderOffsets.options.forEach { minutes ->
                            FilterChip(
                                selected = form.reminderOffsetMinutes == minutes,
                                onClick = { viewModel.update { it.copy(reminderOffsetMinutes = minutes) } },
                                label = { Text(stringResource(reminderOffsetLabelRes(minutes))) },
                            )
                        }
                    }
                    if (!notificationsOk) {
                        Text(stringResource(R.string.followup_notifications_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        VCardlyTextButton(stringResource(R.string.settings_open_notification_settings), onClick = {
                            context.startActivity(ReminderPermissions.notificationSettingsIntent(context))
                        })
                    }
                }

                VCardlyTextField(
                    value = form.notes,
                    onValueChange = { v -> viewModel.update(FollowUpField.NOTES) { it.copy(notes = v) } },
                    label = stringResource(R.string.field_notes),
                    errorText = state.errors[FollowUpField.NOTES]?.let { stringResource(validationMessageRes(it)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = DueTime.dateToPickerMillis(dueDateTime.toLocalDate()))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                VCardlyTextButton(stringResource(R.string.common_ok), onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        val date = DueTime.pickerMillisToDate(picked)
                        viewModel.update(FollowUpField.DUE) { it.copy(dueAt = DueTime.toEpochMillis(date, dueDateTime.toLocalTime(), state.zone)) }
                    }
                    showDate = false
                })
            },
            dismissButton = { VCardlyTextButton(stringResource(R.string.common_cancel), onClick = { showDate = false }) },
        ) { DatePicker(state = pickerState) }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = dueDateTime.hour,
            initialMinute = dueDateTime.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                VCardlyTextButton(stringResource(R.string.common_ok), onClick = {
                    val time = LocalTime.of(timeState.hour, timeState.minute)
                    viewModel.update(FollowUpField.DUE) { it.copy(dueAt = DueTime.toEpochMillis(dueDateTime.toLocalDate(), time, state.zone)) }
                    showTime = false
                })
            },
            dismissButton = { VCardlyTextButton(stringResource(R.string.common_cancel), onClick = { showTime = false }) },
        )
    }

    if (showPicker) ContactPickerDialog(viewModel, onDismiss = { showPicker = false })

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmText = stringResource(R.string.discard_confirm),
            onConfirm = { confirmDiscard = false; onNavigateUp() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun ContactPickerDialog(viewModel: FollowUpEditViewModel, onDismiss: () -> Unit) {
    val query by viewModel.pickerQueryText.collectAsStateWithLifecycle()
    val results by viewModel.pickerResults.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.followup_contact_pick)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setPickerQuery,
                    singleLine = true,
                    label = { Text(stringResource(R.string.contacts_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (results.isEmpty()) {
                    Text(stringResource(R.string.contacts_no_results_title), modifier = Modifier.padding(top = MaterialTheme.spacing.md))
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.contact.id }) { details ->
                        Text(
                            details.contact.fullName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MaterialTheme.spacing.minTouchTarget)
                                .clickable { viewModel.pickContact(details); onDismiss() }
                                .padding(vertical = MaterialTheme.spacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { VCardlyTextButton(stringResource(R.string.common_cancel), onClick = onDismiss) },
    )
}
