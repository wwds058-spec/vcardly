package com.yasin.vcardly.presentation.followups

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyAvatar
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyChip
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySearchBar
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTag
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.core.designsystem.component.VCardlyTonalButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.CardShape
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.core.notifications.ReminderPermissions
import com.yasin.vcardly.domain.model.FollowUpStatus
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
    val colors = MaterialTheme.vcColors
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var notificationsOk by remember { mutableStateOf(ReminderPermissions.notificationsEnabled(context)) }

    // Asked in context, only when the user turns a reminder on.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = ReminderPermissions.notificationsEnabled(context)
    }

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
    val requestUp = { if (state.isDirty) confirmDiscard = true else onNavigateUp() }
    BackHandler(enabled = state.isDirty, onBack = requestUp)

    val dueDateTime = remember(form.dueAt, state.zone) { java.time.Instant.ofEpochMilli(form.dueAt).atZone(state.zone) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        VCardlyTopBar(
            title = stringResource(if (state.isNew) R.string.followup_add else R.string.followup_edit),
            onNavigateUp = requestUp,
        )
        when {
            state.isLoading -> VCardlyLoadingState(kind = SkeletonKind.DETAIL)
            state.notFound -> VCardlyEmptyState(Icons.Rounded.ErrorOutline, stringResource(R.string.followup_not_found_title), stringResource(R.string.followup_not_found_message))
            else -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.screen, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    state.status?.takeIf { it != FollowUpStatus.PENDING }?.let { status ->
                        VCardlyTag(stringResource(status.labelRes()), when (status) {
                            FollowUpStatus.COMPLETED -> colors.mint
                            FollowUpStatus.CANCELLED -> colors.rose
                            else -> colors.orange
                        })
                    }

                    // Contact
                    val contactError = state.errors[FollowUpField.CONTACT]?.let { stringResource(validationMessageRes(it)) }
                    Column {
                        Text(stringResource(R.string.followup_contact), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        VCardlyCard(Modifier.fillMaxWidth(), onClick = { showPicker = true }, onClickLabel = stringResource(R.string.followup_contact_pick), contentPadding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (form.contactId != 0L) VCardlyAvatar(form.contactName, null, size = 40.dp)
                                else IconBadge(Icons.Rounded.PersonSearch, colors.blue, size = 40.dp, circle = true)
                                Text(
                                    form.contactName.ifBlank { stringResource(R.string.followup_contact_pick) },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (form.contactId != 0L) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                                )
                                Text(stringResource(R.string.common_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        contactError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
                    }

                    VCardlyTextField(
                        value = form.title,
                        onValueChange = { v -> viewModel.update(FollowUpField.TITLE) { it.copy(title = v) } },
                        label = stringResource(R.string.followup_title_label),
                        placeholder = stringResource(R.string.hint_followup_title),
                        required = true,
                        errorText = state.errors[FollowUpField.TITLE]?.let { stringResource(validationMessageRes(it)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.followup_type), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                        val types = FollowUpType.pickable + listOfNotNull(form.type.takeIf { it !in FollowUpType.pickable })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            types.forEach { type ->
                                VCardlyChip(stringResource(type.labelRes()), form.type == type, { viewModel.update { it.copy(type = type) } }, leadingIcon = type.icon)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.followup_due), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            VCardlyTonalButton(dateFormat.format(Date(form.dueAt)), onClick = { showDate = true }, leadingIcon = Icons.Rounded.Event, modifier = Modifier.weight(1f))
                            VCardlyTonalButton(timeFormat.format(Date(form.dueAt)), onClick = { showTime = true }, leadingIcon = Icons.Rounded.Schedule, modifier = Modifier.weight(1f))
                        }
                        // One-tap scheduling for the usual cases.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val now = remember { System.currentTimeMillis() }
                            QuickDue(stringResource(R.string.due_quick_hour)) { viewModel.update(FollowUpField.DUE) { it.copy(dueAt = now + 3_600_000L - now % 60_000L) } }
                            QuickDue(stringResource(R.string.due_quick_tomorrow)) {
                                val t = java.time.LocalDate.now(state.zone).plusDays(1)
                                viewModel.update(FollowUpField.DUE) { it.copy(dueAt = DueTime.toEpochMillis(t, LocalTime.of(9, 0), state.zone)) }
                            }
                            QuickDue(stringResource(R.string.due_quick_next_week)) {
                                val t = java.time.LocalDate.now(state.zone).plusWeeks(1)
                                viewModel.update(FollowUpField.DUE) { it.copy(dueAt = DueTime.toEpochMillis(t, LocalTime.of(9, 0), state.zone)) }
                            }
                        }
                        state.errors[FollowUpField.DUE]?.let { Text(stringResource(validationMessageRes(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }

                    VCardlyCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = form.reminderEnabled, role = Role.Switch) { on ->
                                viewModel.update { it.copy(reminderEnabled = on) }
                                if (on && Build.VERSION.SDK_INT >= 33 && !notificationsOk) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(if (form.reminderEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff, colors.orange, size = 40.dp, circle = true)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(stringResource(R.string.followup_reminder), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.followup_reminder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = form.reminderEnabled, onCheckedChange = null)
                        }
                        AnimatedVisibility(form.reminderEnabled) {
                            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ReminderOffsets.options.forEach { minutes ->
                                        VCardlyChip(stringResource(reminderOffsetLabelRes(minutes)), form.reminderOffsetMinutes == minutes, { viewModel.update { it.copy(reminderOffsetMinutes = minutes) } })
                                    }
                                }
                                if (!notificationsOk) {
                                    VCardlyNotice(stringResource(R.string.followup_notifications_off), colors.rose, Icons.Rounded.NotificationsOff)
                                    VCardlyTextButton(stringResource(R.string.settings_open_notification_settings), onClick = {
                                        context.startActivity(ReminderPermissions.notificationSettingsIntent(context))
                                    })
                                }
                            }
                        }
                    }

                    VCardlyTextField(
                        value = form.notes,
                        onValueChange = { v -> viewModel.update(FollowUpField.NOTES) { it.copy(notes = v) } },
                        label = stringResource(R.string.field_notes),
                        placeholder = stringResource(R.string.hint_followup_notes),
                        errorText = state.errors[FollowUpField.NOTES]?.let { stringResource(validationMessageRes(it)) },
                        singleLine = false,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!state.isNew) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val active = state.status?.isActive == true
                            if (active) {
                                VCardlyTonalButton(stringResource(R.string.followup_mark_done), onClick = viewModel::complete, leadingIcon = Icons.Rounded.CheckCircle, modifier = Modifier.fillMaxWidth())
                                VCardlySecondaryButton(stringResource(R.string.followup_cancel), onClick = viewModel::cancelFollowUp, leadingIcon = Icons.Rounded.Cancel, modifier = Modifier.fillMaxWidth())
                            } else {
                                VCardlyTonalButton(stringResource(R.string.followup_reopen), onClick = viewModel::reopen, leadingIcon = Icons.Rounded.Replay, modifier = Modifier.fillMaxWidth())
                            }
                            VCardlyTextButton(stringResource(R.string.followup_delete), onClick = { confirmDelete = true }, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                VCardlyPrimaryButton(
                    stringResource(R.string.followup_save),
                    onClick = viewModel::save,
                    loading = state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.screen, vertical = 12.dp),
                )
            }
        }
    }

    if (showDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = DueTime.dateToPickerMillis(dueDateTime.toLocalDate()))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            shape = CardShape,
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
            shape = CardShape,
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
            destructive = true,
            onConfirm = { confirmDiscard = false; onNavigateUp() },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.followup_delete_title),
            message = stringResource(R.string.followup_delete_message, form.title),
            confirmText = stringResource(R.string.contact_delete),
            destructive = true,
            icon = Icons.Rounded.DeleteOutline,
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun QuickDue(label: String, onClick: () -> Unit) {
    VCardlyChip(label, selected = false, onClick = onClick, leadingIcon = Icons.Rounded.Schedule)
}

@Composable
private fun ContactPickerDialog(viewModel: FollowUpEditViewModel, onDismiss: () -> Unit) {
    val query by viewModel.pickerQueryText.collectAsStateWithLifecycle()
    val results by viewModel.pickerResults.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.followup_contact_pick)) },
        text = {
            Column {
                VCardlySearchBar(query, viewModel::setPickerQuery, stringResource(R.string.home_search_placeholder))
                if (results.isEmpty()) {
                    Text(stringResource(R.string.contacts_no_results_title), modifier = Modifier.padding(top = MaterialTheme.spacing.md), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(results, key = { it.contact.id }) { details ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { viewModel.pickContact(details); onDismiss() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VCardlyAvatar(details.contact.fullName, details.category?.colorArgb, size = 36.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(details.contact.fullName, style = MaterialTheme.typography.titleSmall)
                                if (details.contact.company.isNotBlank()) {
                                    Text(details.contact.company, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { VCardlyTextButton(stringResource(R.string.common_cancel), onClick = onDismiss) },
    )
}
