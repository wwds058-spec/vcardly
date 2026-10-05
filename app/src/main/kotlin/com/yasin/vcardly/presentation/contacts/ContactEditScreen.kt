package com.yasin.vcardly.presentation.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.image.CardImageRef
import com.yasin.vcardly.presentation.common.CardImageView
import com.yasin.vcardly.presentation.common.FormField
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.LoadingState
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.presentation.common.displayName
import com.yasin.vcardly.presentation.common.validationMessageRes

/** Add (contactId = 0) or edit. [onSaved] receives the saved id and decides where to go. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactEditScreen(
    onNavigateUp: () -> Unit,
    onSaved: (id: Long, wasNew: Boolean) -> Unit,
    viewModel: ContactEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val form = state.form
    val errors = state.errors

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { onSaved(it.id, it.wasNew) }
    }

    val requestUp = { if (state.isDirty) confirmDiscard = true else onNavigateUp() }
    BackHandler(enabled = state.isDirty, onBack = requestUp)

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        VCardlyTopBar(
            title = stringResource(if (state.isNew) R.string.contact_add_title else R.string.contact_edit_title),
            onNavigateUp = requestUp,
            actions = {
                VCardlyTextButton(
                    text = stringResource(R.string.common_save),
                    onClick = viewModel::save,
                    enabled = !state.isLoading && !state.isSaving && !state.notFound,
                )
            },
        )

        when {
            state.isLoading -> LoadingState()
            state.notFound -> EmptyState(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.contact_not_found_title),
                message = stringResource(R.string.contact_not_found_message),
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                if (state.isFromScan) {
                    ReviewBanner(ocrFailed = state.ocrFailed)
                }
                if (state.saveFailed) {
                    Text(
                        stringResource(R.string.contact_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                CardImagesSection(
                    front = form.frontImage,
                    back = form.backImage,
                    onRemoveFront = { viewModel.removeImage(front = true) },
                    onRemoveBack = { viewModel.removeImage(front = false) },
                )
                val onChange = viewModel::update
                FormField(ContactField.FULL_NAME, R.string.field_full_name, form.fullName, errors, caps = KeyboardCapitalization.Words) { v -> onChange(ContactField.FULL_NAME) { it.copy(fullName = v) } }
                FormField(ContactField.JOB_TITLE, R.string.field_job_title, form.jobTitle, errors, caps = KeyboardCapitalization.Words) { v -> onChange(ContactField.JOB_TITLE) { it.copy(jobTitle = v) } }
                FormField(ContactField.COMPANY, R.string.field_company, form.company, errors, caps = KeyboardCapitalization.Words) { v -> onChange(ContactField.COMPANY) { it.copy(company = v) } }
                FormField(ContactField.PHONE, R.string.field_phone, form.phone, errors, KeyboardType.Phone) { v -> onChange(ContactField.PHONE) { it.copy(phone = v) } }
                FormField(ContactField.PHONE_ALT, R.string.field_phone_alt, form.phoneAlt, errors, KeyboardType.Phone) { v -> onChange(ContactField.PHONE_ALT) { it.copy(phoneAlt = v) } }
                FormField(ContactField.EMAIL, R.string.field_email, form.email, errors, KeyboardType.Email) { v -> onChange(ContactField.EMAIL) { it.copy(email = v) } }
                FormField(ContactField.EMAIL_ALT, R.string.field_email_alt, form.emailAlt, errors, KeyboardType.Email) { v -> onChange(ContactField.EMAIL_ALT) { it.copy(emailAlt = v) } }
                FormField(ContactField.WEBSITE, R.string.field_website, form.website, errors, KeyboardType.Uri) { v -> onChange(ContactField.WEBSITE) { it.copy(website = v) } }
                FormField(ContactField.ADDRESS, R.string.field_address, form.address, errors, caps = KeyboardCapitalization.Words, singleLine = false) { v -> onChange(ContactField.ADDRESS) { it.copy(address = v) } }
                FormField(ContactField.NOTES, R.string.field_notes, form.notes, errors, caps = KeyboardCapitalization.Sentences, singleLine = false) { v -> onChange(ContactField.NOTES) { it.copy(notes = v) } }

                SectionHeader(stringResource(R.string.field_category))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    FilterChip(
                        selected = form.categoryId == null,
                        onClick = { viewModel.update { it.copy(categoryId = null) } },
                        label = { Text(stringResource(R.string.category_uncategorised)) },
                    )
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = form.categoryId == category.id,
                            onClick = { viewModel.update { it.copy(categoryId = category.id) } },
                            label = { Text(category.displayName().asString()) },
                        )
                    }
                }

                SectionHeader(stringResource(R.string.field_tags))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in form.tagIds,
                            onClick = { viewModel.toggleTag(tag.id) },
                            label = { Text("#${tag.name}") },
                        )
                    }
                }
                NewTagField(onAdd = { name, done -> viewModel.addTag(name, done) })

                if (state.unmatchedLines.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.scan_unmatched_title))
                    Text(
                        stringResource(R.string.scan_unmatched_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.unmatchedLines.forEach { line ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(line, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            VCardlyTextButton(stringResource(R.string.scan_add_to_notes), onClick = { viewModel.addLineToNotes(line) })
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmText = stringResource(R.string.discard_confirm),
            onConfirm = {
                confirmDiscard = false
                onNavigateUp()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun NewTagField(onAdd: (String, (Boolean) -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = { onAdd(text) { ok -> if (ok) text = "" } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 40) text = it },
            label = { Text(stringResource(R.string.tag_new_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tag_add))
        }
    }
}

/** OCR output is a guess. This says so, every time, before the user can save. */
@Composable
private fun ReviewBanner(ocrFailed: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Text(
            stringResource(if (ocrFailed) R.string.scan_review_ocr_failed else R.string.scan_review_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(MaterialTheme.spacing.md),
        )
    }
}

@Composable
private fun CardImagesSection(
    front: CardImageRef?,
    back: CardImageRef?,
    onRemoveFront: () -> Unit,
    onRemoveBack: () -> Unit,
) {
    if (front == null && back == null) return
    SectionHeader(stringResource(R.string.field_card_images))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        front?.let { CardImageSlot(it, stringResource(R.string.card_front), onRemoveFront, Modifier.weight(1f)) }
        back?.let { CardImageSlot(it, stringResource(R.string.card_back), onRemoveBack, Modifier.weight(1f)) }
    }
}

@Composable
private fun CardImageSlot(ref: CardImageRef, label: String, onRemove: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        CardImageView(ref, contentDescription = label, modifier = Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = MaterialTheme.spacing.xs))
        VCardlyTextButton(stringResource(R.string.card_remove_image, label), onClick = onRemove)
    }
}
