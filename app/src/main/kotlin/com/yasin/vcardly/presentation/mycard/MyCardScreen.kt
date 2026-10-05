package com.yasin.vcardly.presentation.mycard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.core.designsystem.component.VCardlyTonalButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.MyCard
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.presentation.common.VCardlyDigitalCard
import com.yasin.vcardly.presentation.common.validationMessageRes
import com.yasin.vcardly.presentation.share.QrSharePanel

@Composable
fun MyCardScreen(onNavigateUp: () -> Unit, onEdit: () -> Unit, viewModel: MyCardViewModel = hiltViewModel()) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    MyCardContent(card, onNavigateUp, onEdit)
}

/** Stateless "My digital card" (used directly by UI tests). [card] null = loading. */
@Composable
fun MyCardContent(
    card: MyCard?,
    onNavigateUp: () -> Unit,
    onEdit: () -> Unit,
    sharePanel: @Composable (com.yasin.vcardly.domain.vcard.ShareCard) -> Unit = { QrSharePanel(it) },
) {
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(
            title = stringResource(R.string.mycard_title),
            onNavigateUp = onNavigateUp,
            actions = {
                if (card?.isEmpty == false) {
                    VCardlyTonalButton(stringResource(R.string.contact_edit), onClick = onEdit, leadingIcon = Icons.Rounded.Edit, modifier = Modifier.padding(end = 8.dp))
                }
            },
        )
        when {
            card == null -> VCardlyLoadingState(kind = SkeletonKind.DETAIL)
            card.isEmpty -> VCardlyEmptyState(
                icon = Icons.Rounded.Badge,
                title = stringResource(R.string.mycard_empty_title),
                message = stringResource(R.string.mycard_empty_message),
                tone = MaterialTheme.vcColors.lavender,
                action = { VCardlyPrimaryButton(stringResource(R.string.mycard_create), onClick = onEdit, leadingIcon = Icons.Rounded.Edit) },
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = MaterialTheme.spacing.screen, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                VCardlyDigitalCard(card)
                sharePanel(card.toShareCard())
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
        VCardlyTopBar(title = stringResource(R.string.mycard_edit_title), onNavigateUp = requestUp)
        if (state.isLoading) VCardlyLoadingState(kind = SkeletonKind.DETAIL) else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.screen, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val up = viewModel::update
                CardField(card.fullName, errors, ContactField.FULL_NAME, R.string.field_full_name, R.string.hint_full_name, Icons.Rounded.Person, caps = KeyboardCapitalization.Words, required = true) { v -> up(ContactField.FULL_NAME) { it.copy(fullName = v) } }
                CardField(card.jobTitle, errors, ContactField.JOB_TITLE, R.string.field_designation, R.string.hint_designation, Icons.Rounded.Badge, caps = KeyboardCapitalization.Words) { v -> up(ContactField.JOB_TITLE) { it.copy(jobTitle = v) } }
                CardField(card.company, errors, ContactField.COMPANY, R.string.field_company, R.string.hint_company, Icons.Rounded.Business, caps = KeyboardCapitalization.Words) { v -> up(ContactField.COMPANY) { it.copy(company = v) } }
                CardField(card.phone, errors, ContactField.PHONE, R.string.field_phone, R.string.hint_phone, Icons.Rounded.Call, KeyboardType.Phone) { v -> up(ContactField.PHONE) { it.copy(phone = v) } }
                CardField(card.phoneAlt, errors, ContactField.PHONE_ALT, R.string.field_phone_alt, R.string.hint_phone, Icons.Rounded.Call, KeyboardType.Phone) { v -> up(ContactField.PHONE_ALT) { it.copy(phoneAlt = v) } }
                CardField(card.email, errors, ContactField.EMAIL, R.string.field_email, R.string.hint_email, Icons.Rounded.Email, KeyboardType.Email) { v -> up(ContactField.EMAIL) { it.copy(email = v) } }
                CardField(card.emailAlt, errors, ContactField.EMAIL_ALT, R.string.field_email_alt, R.string.hint_email, Icons.Rounded.Email, KeyboardType.Email) { v -> up(ContactField.EMAIL_ALT) { it.copy(emailAlt = v) } }
                CardField(card.website, errors, ContactField.WEBSITE, R.string.field_website, R.string.hint_website, Icons.Rounded.Language, KeyboardType.Uri) { v -> up(ContactField.WEBSITE) { it.copy(website = v) } }
                CardField(card.address, errors, ContactField.ADDRESS, R.string.field_address, R.string.hint_address, Icons.Rounded.LocationOn, caps = KeyboardCapitalization.Words, singleLine = false) { v -> up(ContactField.ADDRESS) { it.copy(address = v) } }
            }
            VCardlyPrimaryButton(
                stringResource(R.string.mycard_save),
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.screen, vertical = 12.dp),
            )
        }
    }

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
}

@Composable
private fun CardField(
    value: String,
    errors: Map<ContactField, Reason>,
    field: ContactField,
    labelRes: Int,
    hintRes: Int,
    icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    required: Boolean = false,
    onChange: (String) -> Unit,
) {
    VCardlyTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(labelRes),
        placeholder = stringResource(hintRes),
        leadingIcon = icon,
        required = required,
        singleLine = singleLine,
        errorText = errors[field]?.let { stringResource(validationMessageRes(field, it)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = caps, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = Modifier.fillMaxWidth(),
    )
}
