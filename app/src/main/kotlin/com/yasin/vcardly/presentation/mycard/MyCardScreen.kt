package com.yasin.vcardly.presentation.mycard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.model.MyCard
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.presentation.common.FormField
import com.yasin.vcardly.presentation.share.QrSharePanel

@Composable
fun MyCardScreen(onNavigateUp: () -> Unit, onEdit: () -> Unit, viewModel: MyCardViewModel = hiltViewModel()) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(
            title = stringResource(R.string.mycard_title),
            onNavigateUp = onNavigateUp,
            actions = {
                if (card?.isEmpty == false) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.contact_edit)) }
                }
            },
        )
        val c = card
        when {
            c == null -> VCardlyLoadingState()
            c.isEmpty -> VCardlyEmptyState(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.mycard_empty_title),
                message = stringResource(R.string.mycard_empty_message),
                action = { VCardlyPrimaryButton(stringResource(R.string.mycard_create), onClick = onEdit) },
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            ) {
                DigitalCard(c)
                QrSharePanel(c.toShareCard())
            }
        }
    }
}

/** The on-screen business card. */
@Composable
private fun DigitalCard(card: MyCard) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Text(card.fullName, style = MaterialTheme.typography.headlineMedium)
            listOf(card.jobTitle, card.company).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }
                ?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            listOf(card.phone, card.phoneAlt, card.email, card.emailAlt, card.website, card.address).filter { it.isNotBlank() }.forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun MyCardEditScreen(onNavigateUp: () -> Unit, viewModel: MyCardEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val card = state.card
    val errors = state.errors

    LaunchedEffect(viewModel) { viewModel.saved.collect { onNavigateUp() } }
    val requestUp = { if (state.isDirty) confirmDiscard = true else onNavigateUp() }
    BackHandler(enabled = state.isDirty, onBack = requestUp)

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        VCardlyTopBar(
            title = stringResource(R.string.mycard_edit_title),
            onNavigateUp = requestUp,
            actions = { VCardlyTextButton(stringResource(R.string.common_save), onClick = viewModel::save, enabled = !state.isLoading) },
        )
        if (state.isLoading) VCardlyLoadingState() else Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            val up = viewModel::update
            FormField(ContactField.FULL_NAME, R.string.field_full_name, card.fullName, errors, caps = KeyboardCapitalization.Words) { v -> up(ContactField.FULL_NAME) { it.copy(fullName = v) } }
            FormField(ContactField.JOB_TITLE, R.string.field_job_title, card.jobTitle, errors, caps = KeyboardCapitalization.Words) { v -> up(ContactField.JOB_TITLE) { it.copy(jobTitle = v) } }
            FormField(ContactField.COMPANY, R.string.field_company, card.company, errors, caps = KeyboardCapitalization.Words) { v -> up(ContactField.COMPANY) { it.copy(company = v) } }
            FormField(ContactField.PHONE, R.string.field_phone, card.phone, errors, KeyboardType.Phone) { v -> up(ContactField.PHONE) { it.copy(phone = v) } }
            FormField(ContactField.PHONE_ALT, R.string.field_phone_alt, card.phoneAlt, errors, KeyboardType.Phone) { v -> up(ContactField.PHONE_ALT) { it.copy(phoneAlt = v) } }
            FormField(ContactField.EMAIL, R.string.field_email, card.email, errors, KeyboardType.Email) { v -> up(ContactField.EMAIL) { it.copy(email = v) } }
            FormField(ContactField.EMAIL_ALT, R.string.field_email_alt, card.emailAlt, errors, KeyboardType.Email) { v -> up(ContactField.EMAIL_ALT) { it.copy(emailAlt = v) } }
            FormField(ContactField.WEBSITE, R.string.field_website, card.website, errors, KeyboardType.Uri) { v -> up(ContactField.WEBSITE) { it.copy(website = v) } }
            FormField(ContactField.ADDRESS, R.string.field_address, card.address, errors, caps = KeyboardCapitalization.Words, singleLine = false) { v -> up(ContactField.ADDRESS) { it.copy(address = v) } }
        }
    }

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
