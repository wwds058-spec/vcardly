package com.yasin.vcardly.presentation.contacts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.LoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.presentation.common.ContactAvatar
import com.yasin.vcardly.presentation.common.displayName
import java.text.DateFormat
import java.util.Date

@Composable
fun ContactDetailScreen(
    onNavigateUp: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val content = state as? ContactDetailUiState.Content

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(
            title = stringResource(R.string.contact_details_title),
            onNavigateUp = onNavigateUp,
            actions = {
                if (content != null) {
                    val contact = content.details.contact
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (contact.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(
                                if (contact.isFavorite) R.string.contact_remove_favorite else R.string.contact_add_favorite,
                                contact.fullName,
                            ),
                        )
                    }
                    IconButton(onClick = { onEdit(contact.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.contact_edit))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.contact_delete))
                    }
                }
            },
        )
        when (val s = state) {
            ContactDetailUiState.Loading -> LoadingState()
            ContactDetailUiState.NotFound -> EmptyState(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.contact_not_found_title),
                message = stringResource(R.string.contact_not_found_message),
            )
            is ContactDetailUiState.Content -> DetailContent(s.details)
        }
    }

    if (confirmDelete && content != null) {
        ConfirmDialog(
            title = stringResource(R.string.contact_delete_title),
            message = stringResource(R.string.contact_delete_message, content.details.contact.fullName),
            confirmText = stringResource(R.string.contact_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone = onNavigateUp)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(details: ContactDetails) {
    val contact = details.contact
    val context = LocalContext.current
    val spacing = MaterialTheme.spacing
    val subtitle = listOf(contact.jobTitle, contact.company).filter { it.isNotBlank() }.joinToString(" · ")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ContactAvatar(contact.fullName, details.category?.colorArgb, size = 80.dp)
            Text(
                contact.fullName,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.sm),
            )
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }

        if (details.category != null || details.tags.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally),
            ) {
                details.category?.let { AssistChip(onClick = {}, enabled = false, label = { Text(it.displayName().asString()) }) }
                details.tags.forEach { AssistChip(onClick = {}, enabled = false, label = { Text("#${it.name}") }) }
            }
        }

        FieldRow(R.string.field_phone, contact.phone, Icons.Filled.Call, R.string.action_call) { context.dial(contact.phone) }
        FieldRow(R.string.field_phone_alt, contact.phoneAlt, Icons.Filled.Call, R.string.action_call) { context.dial(contact.phoneAlt) }
        FieldRow(R.string.field_email, contact.email, Icons.Filled.Email, R.string.action_email) { context.mail(contact.email) }
        FieldRow(R.string.field_email_alt, contact.emailAlt, Icons.Filled.Email, R.string.action_email) { context.mail(contact.emailAlt) }
        FieldRow(R.string.field_website, contact.website, null, null) { context.openWebsite(contact.website) }
        FieldRow(R.string.field_address, contact.address)
        FieldRow(R.string.field_notes, contact.notes)

        val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        Text(
            stringResource(R.string.contact_added_on, formatter.format(Date(contact.createdAt))),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.md),
        )
    }
}

/** Shows nothing for blank values. With [onClick] the whole row is tappable; [actionIcon] is a visual hint only. */
@Composable
private fun FieldRow(
    labelRes: Int,
    value: String,
    actionIcon: ImageVector? = null,
    actionLabelRes: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    if (value.isBlank()) return
    val label = stringResource(labelRes)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.spacing.minTouchTarget)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = actionLabelRes?.let { stringResource(it) }, onClick = onClick) else Modifier)
            .padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (actionIcon != null) Icon(actionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ---- external intents: no permissions needed; failures are swallowed (no handler installed) ----

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Device has no app for this action; nothing useful to do.
    }
}

private fun Context.dial(number: String) =
    startSafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.trim(), null)))

private fun Context.mail(address: String) =
    startSafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address.trim(), null)))

private fun Context.openWebsite(site: String) {
    val trimmed = site.trim()
    val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
    startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(withScheme)))
}
