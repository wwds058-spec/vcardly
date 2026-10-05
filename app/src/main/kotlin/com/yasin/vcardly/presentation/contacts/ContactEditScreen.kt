package com.yasin.vcardly.presentation.contacts

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyChip
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyIconButton
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyStepper
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.core.image.CardImageRef
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.presentation.common.BusinessCardArt
import com.yasin.vcardly.presentation.common.CardImageView
import com.yasin.vcardly.presentation.common.displayName
import com.yasin.vcardly.presentation.common.validationMessageRes
import java.io.File

/** User intents from the contact form. */
data class ContactEditActions(
    val onUpdate: (ContactField?, (ContactForm) -> ContactForm) -> Unit = { _, _ -> },
    val onToggleTag: (Long) -> Unit = {},
    val onAddTag: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    val onRemoveImage: (front: Boolean) -> Unit = {},
    val onRotateImage: (front: Boolean) -> Unit = {},
    val onTakePhoto: (front: Boolean) -> Unit = {},
    val onPickImage: (front: Boolean) -> Unit = {},
    val onAddLineToNotes: (String) -> Unit = {},
    val onValidate: (Set<ContactField>) -> Boolean = { true },
    val onSave: () -> Unit = {},
    val onNavigateUp: () -> Unit = {},
    val onRescan: (() -> Unit)? = null,
)

/** Form steps. Each lists the fields it validates before moving on. */
enum class FormStep(val titleRes: Int, val fields: Set<ContactField>) {
    BASIC(R.string.form_step_basic, setOf(ContactField.FULL_NAME, ContactField.COMPANY, ContactField.JOB_TITLE)),
    CONTACT(R.string.form_step_contact, setOf(ContactField.PHONE, ContactField.PHONE_ALT, ContactField.EMAIL, ContactField.EMAIL_ALT, ContactField.WEBSITE)),
    BUSINESS(R.string.form_step_business, setOf(ContactField.ADDRESS)),
    DETAILS(R.string.form_step_details, setOf(ContactField.NOTES)),
    FINISH(R.string.form_step_finish, emptySet()),
}

/** Add (contactId = 0) or edit. After a scan this is the review screen. [onSaved] receives the saved id. */
@Composable
fun ContactEditScreen(
    onNavigateUp: () -> Unit,
    onSaved: (id: Long, wasNew: Boolean) -> Unit,
    onRescan: (() -> Unit)? = null,
    viewModel: ContactEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved(it.id, it.wasNew) } }

    // Camera (the user's camera app writes into a one-off FileProvider URI) and the system photo picker.
    var cameraTarget by remember { mutableStateOf<Pair<Boolean, File>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        cameraTarget?.let { (front, file) -> viewModel.onCameraResult(front, file, ok) }
        cameraTarget = null
    }
    // An app that declares CAMERA must hold it before using the system camera intent (Android throws otherwise),
    // so the permission is asked here, in context, the first time the user chooses "Take photo".
    val launchCamera: (Boolean) -> Unit = { front ->
        val file = viewModel.newCameraFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraTarget = front to file
        try {
            camera.launch(uri)
        } catch (_: android.content.ActivityNotFoundException) {
            cameraTarget = null
        } catch (_: SecurityException) {
            cameraTarget = null
        }
    }
    var permissionFront by remember { mutableStateOf(true) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera(permissionFront)
    }
    var pickFront by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.onImagePicked(pickFront, uri)
    }

    val requestUp = { if (state.isDirty) confirmDiscard = true else onNavigateUp() }
    BackHandler(enabled = state.isDirty, onBack = requestUp)

    ContactEditContent(
        state = state,
        actions = ContactEditActions(
            onUpdate = { field, t -> viewModel.update(field, t) },
            onToggleTag = viewModel::toggleTag,
            onAddTag = { name, done -> viewModel.addTag(name, done) },
            onRemoveImage = viewModel::removeImage,
            onRotateImage = viewModel::rotateImage,
            onTakePhoto = { front ->
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera(front) else { permissionFront = front; cameraPermission.launch(android.Manifest.permission.CAMERA) }
            },
            onPickImage = { front -> pickFront = front; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAddLineToNotes = viewModel::addLineToNotes,
            onValidate = viewModel::validateFields,
            onSave = viewModel::save,
            onNavigateUp = requestUp,
            onRescan = onRescan,
        ),
    )

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

/** Stateless form content (used directly by UI tests). */
@Composable
fun ContactEditContent(state: ContactEditUiState, actions: ContactEditActions, initialStep: FormStep = FormStep.BASIC) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        when {
            state.isLoading -> {
                VCardlyTopBar(title = stringResource(R.string.contact_edit_title), onNavigateUp = actions.onNavigateUp)
                VCardlyLoadingState(kind = SkeletonKind.DETAIL)
            }
            state.notFound -> {
                VCardlyTopBar(title = stringResource(R.string.contact_edit_title), onNavigateUp = actions.onNavigateUp)
                VCardlyEmptyState(
                    icon = Icons.Rounded.ErrorOutline,
                    title = stringResource(R.string.contact_not_found_title),
                    message = stringResource(R.string.contact_not_found_message),
                )
            }
            state.isFromScan -> ReviewForm(state, actions)
            else -> SteppedForm(state, actions, initialStep)
        }
    }
}

// ---------------------------------------------------------------- stepped form (add / edit)

@Composable
private fun ColumnScope.SteppedForm(state: ContactEditUiState, actions: ContactEditActions, initialStep: FormStep) {
    var step by rememberSaveable { mutableIntStateOf(initialStep.ordinal) }
    val steps = FormStep.entries
    val current = steps[step]
    // A save attempt that fails validation jumps back to the first step with an error.
    LaunchedEffect(state.errors) {
        val first = steps.indexOfFirst { s -> s.fields.any { it in state.errors } }
        if (first in 0 until step) step = first
    }
    BackHandler(enabled = step > 0 && !state.isDirty) { step-- }

    VCardlyTopBar(
        title = stringResource(if (state.isNew) R.string.contact_add_title else R.string.contact_edit_title),
        onNavigateUp = actions.onNavigateUp,
        actions = {
            VCardlyTextButton(stringResource(R.string.common_save), onClick = actions.onSave, enabled = !state.isSaving)
        },
    )
    VCardlyStepper(
        steps = steps.map { stringResource(it.titleRes) },
        current = step,
        onStepClick = { step = it },
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.screen, vertical = 8.dp),
    )
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState > initialState
            (slideInHorizontally(tween(260)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(220))) togetherWith fadeOut(tween(120))
        },
        modifier = Modifier.weight(1f),
        label = "formStep",
    ) { index ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.screen, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (state.saveFailed) VCardlyNotice(stringResource(R.string.contact_save_failed), MaterialTheme.vcColors.rose, Icons.Rounded.ErrorOutline)
            when (steps[index]) {
                FormStep.BASIC -> {
                    CardImagesEditor(state, actions)
                    Field(state, actions, ContactField.FULL_NAME, R.string.field_full_name, R.string.hint_full_name, Icons.Rounded.Person, caps = KeyboardCapitalization.Words, required = true) { it.fullName }
                    Field(state, actions, ContactField.COMPANY, R.string.field_company, R.string.hint_company, Icons.Rounded.Business, caps = KeyboardCapitalization.Words) { it.company }
                    Field(state, actions, ContactField.JOB_TITLE, R.string.field_designation, R.string.hint_designation, Icons.Rounded.Badge, caps = KeyboardCapitalization.Words) { it.jobTitle }
                }
                FormStep.CONTACT -> {
                    Field(state, actions, ContactField.PHONE, R.string.field_phone, R.string.hint_phone, Icons.Rounded.Call, KeyboardType.Phone) { it.phone }
                    Field(state, actions, ContactField.PHONE_ALT, R.string.field_phone_alt, R.string.hint_phone, Icons.Rounded.Call, KeyboardType.Phone) { it.phoneAlt }
                    Field(state, actions, ContactField.EMAIL, R.string.field_email, R.string.hint_email, Icons.Rounded.Email, KeyboardType.Email) { it.email }
                    Field(state, actions, ContactField.EMAIL_ALT, R.string.field_email_alt, R.string.hint_email, Icons.Rounded.Email, KeyboardType.Email) { it.emailAlt }
                    Field(state, actions, ContactField.WEBSITE, R.string.field_website, R.string.hint_website, Icons.Rounded.Language, KeyboardType.Uri) { it.website }
                }
                FormStep.BUSINESS -> {
                    CategoryPicker(state, actions)
                    TagPicker(state, actions)
                    Field(state, actions, ContactField.ADDRESS, R.string.field_address, R.string.hint_address, Icons.Rounded.LocationOn, caps = KeyboardCapitalization.Words, singleLine = false) { it.address }
                }
                FormStep.DETAILS -> {
                    Field(state, actions, ContactField.NOTES, R.string.field_notes, R.string.hint_notes, null, caps = KeyboardCapitalization.Sentences, singleLine = false, minLines = 5) { it.notes }
                    UnmatchedLines(state, actions)
                }
                FormStep.FINISH -> FinishSummary(state)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.screen, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (step > 0) {
            VCardlySecondaryButton(stringResource(R.string.common_back), onClick = { step-- }, modifier = Modifier.weight(1f))
        }
        if (current == FormStep.FINISH) {
            VCardlyPrimaryButton(
                stringResource(R.string.contact_save_contact),
                onClick = actions.onSave,
                loading = state.isSaving,
                leadingIcon = Icons.Rounded.Check,
                modifier = Modifier.weight(if (step > 0) 1.6f else 1f),
            )
        } else {
            VCardlyPrimaryButton(
                stringResource(R.string.onboarding_next),
                onClick = { if (actions.onValidate(current.fields)) step++ },
                modifier = Modifier.weight(if (step > 0) 1.6f else 1f),
            )
        }
    }
}

@Composable
private fun Field(
    state: ContactEditUiState,
    actions: ContactEditActions,
    field: ContactField,
    labelRes: Int,
    hintRes: Int,
    icon: ImageVector?,
    keyboard: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    required: Boolean = false,
    minLines: Int = 3,
    value: (ContactForm) -> String,
) {
    val setter: (ContactForm, String) -> ContactForm = { f, v ->
        when (field) {
            ContactField.FULL_NAME -> f.copy(fullName = v)
            ContactField.JOB_TITLE -> f.copy(jobTitle = v)
            ContactField.COMPANY -> f.copy(company = v)
            ContactField.PHONE -> f.copy(phone = v)
            ContactField.PHONE_ALT -> f.copy(phoneAlt = v)
            ContactField.EMAIL -> f.copy(email = v)
            ContactField.EMAIL_ALT -> f.copy(emailAlt = v)
            ContactField.WEBSITE -> f.copy(website = v)
            ContactField.ADDRESS -> f.copy(address = v)
            ContactField.NOTES -> f.copy(notes = v)
        }
    }
    VCardlyTextField(
        value = value(state.form),
        onValueChange = { v -> actions.onUpdate(field) { setter(it, v) } },
        label = stringResource(labelRes),
        placeholder = stringResource(hintRes),
        leadingIcon = icon,
        required = required,
        singleLine = singleLine,
        minLines = minLines,
        errorText = state.errors[field]?.let { stringResource(validationMessageRes(field, it)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = caps, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CardImagesEditor(state: ContactEditUiState, actions: ContactEditActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.field_card_images), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImageSlot(state.form.frontImage, stringResource(R.string.card_front_short), front = true, state.imageBusy, actions, Modifier.weight(1f))
            ImageSlot(state.form.backImage, stringResource(R.string.card_back_short), front = false, state.imageBusy, actions, Modifier.weight(1f))
        }
        if (state.imageFailed) {
            Text(stringResource(R.string.scan_error_import), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

/** One side of the card: empty "add" tile, or the image with replace / rotate / remove. */
@Composable
private fun ImageSlot(ref: CardImageRef?, label: String, front: Boolean, busy: Boolean, actions: ContactEditActions, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        Box {
            if (ref == null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable(enabled = !busy, role = Role.Button, onClickLabel = stringResource(R.string.card_add_image, label)) { menu = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.vcColors.blue.container), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AddAPhoto, null, tint = MaterialTheme.vcColors.blue.accent, modifier = Modifier.size(20.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            } else {
                CardImageView(ref, label, Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button, onClickLabel = stringResource(R.string.card_image_options, label)) { menu = true })
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 3.dp)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.card_take_photo)) }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { menu = false; actions.onTakePhoto(front) })
                DropdownMenuItem(text = { Text(stringResource(R.string.scan_pick_gallery)) }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) }, onClick = { menu = false; actions.onPickImage(front) })
                if (ref != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.scan_rotate_right)) }, leadingIcon = { Icon(Icons.Rounded.RotateRight, null) }, onClick = { menu = false; actions.onRotateImage(front) })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.card_remove), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; actions.onRemoveImage(front) },
                    )
                }
            }
        }
        if (ref != null) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(state: ContactEditUiState, actions: ContactEditActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VCardlyChip(stringResource(R.string.category_uncategorised), state.form.categoryId == null, { actions.onUpdate(null) { it.copy(categoryId = null) } })
            state.categories.forEach { category ->
                VCardlyChip(
                    category.displayName().asString(),
                    state.form.categoryId == category.id,
                    { actions.onUpdate(null) { it.copy(categoryId = category.id) } },
                    dotColor = androidx.compose.ui.graphics.Color(category.colorArgb),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPicker(state: ContactEditUiState, actions: ContactEditActions) {
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) actions.onAddTag(text) { ok -> if (ok) text = "" } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.field_tags), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        if (state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag -> VCardlyChip("#" + tag.name, tag.id in state.form.tagIds, { actions.onToggleTag(tag.id) }) }
            }
        }
        VCardlyTextField(
            value = text,
            onValueChange = { if (it.length <= 40) text = it },
            label = stringResource(R.string.tag_new_hint),
            placeholder = stringResource(R.string.hint_tag),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailing = {
                IconButton(onClick = { submit() }, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tag_add)) }
            },
        )
    }
}

@Composable
private fun UnmatchedLines(state: ContactEditUiState, actions: ContactEditActions) {
    if (state.unmatchedLines.isEmpty()) return
    VCardlyCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scan_unmatched_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.scan_unmatched_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        state.unmatchedLines.forEach { line ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(line, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                VCardlyTextButton(stringResource(R.string.scan_add_to_notes), onClick = { actions.onAddLineToNotes(line) })
            }
        }
    }
}

@Composable
private fun FinishSummary(state: ContactEditUiState) {
    val f = state.form
    Text(stringResource(R.string.form_finish_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.form_finish_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (f.frontImage != null) CardImageView(f.frontImage, stringResource(R.string.card_front), Modifier.fillMaxWidth())
    else BusinessCardArt(f.fullName.ifBlank { stringResource(R.string.field_full_name) }, f.jobTitle, f.company, f.phone, f.email, f.website, Modifier.fillMaxWidth(),
        state.categories.firstOrNull { it.id == f.categoryId }?.colorArgb)
    val filled = listOf(f.phone, f.phoneAlt, f.email, f.emailAlt, f.website, f.address, f.notes).count { it.isNotBlank() } + f.tagIds.size
    Text(pluralStringResource(R.plurals.form_finish_count, filled, filled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- OCR review (after a scan)

@Composable
private fun ColumnScope.ReviewForm(state: ContactEditUiState, actions: ContactEditActions) {
    var manual by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.vcColors
    VCardlyTopBar(title = stringResource(R.string.scan_review_title), onNavigateUp = actions.onNavigateUp)
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.ocrFailed && !manual) {
            // Honest failure: nothing was read. Offer the two next steps.
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(colors.orange.container), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DocumentScanner, null, tint = colors.orange.accent, modifier = Modifier.size(32.dp))
                }
                Text(stringResource(R.string.scan_failed_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp).semantics { heading() })
                Text(stringResource(R.string.scan_failed_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    actions.onRescan?.let { VCardlySecondaryButton(stringResource(R.string.common_retry), onClick = it, leadingIcon = Icons.Rounded.Refresh) }
                    VCardlyPrimaryButton(stringResource(R.string.scan_enter_manually), onClick = { manual = true })
                }
            }
            state.form.frontImage?.let { CardImageView(it, stringResource(R.string.card_front), Modifier.fillMaxWidth()) }
            return@Column
        }
        if (!state.ocrFailed) SuccessHeader(state.detectedCount)
        if (state.saveFailed) VCardlyNotice(stringResource(R.string.contact_save_failed), colors.rose, Icons.Rounded.ErrorOutline)
        VCardlyNotice(stringResource(R.string.scan_review_banner), colors.blue, Icons.Rounded.Description)
        CardImagesEditor(state, actions)
        Field(state, actions, ContactField.FULL_NAME, R.string.field_name, R.string.hint_full_name, Icons.Rounded.Person, caps = KeyboardCapitalization.Words, required = true) { it.fullName }
        Field(state, actions, ContactField.COMPANY, R.string.field_company, R.string.hint_company, Icons.Rounded.Business, caps = KeyboardCapitalization.Words) { it.company }
        Field(state, actions, ContactField.JOB_TITLE, R.string.field_designation, R.string.hint_designation, Icons.Rounded.Badge, caps = KeyboardCapitalization.Words) { it.jobTitle }
        Field(state, actions, ContactField.PHONE, R.string.field_phone, R.string.hint_phone, Icons.Rounded.Call, KeyboardType.Phone) { it.phone }
        Field(state, actions, ContactField.EMAIL, R.string.field_email, R.string.hint_email, Icons.Rounded.Email, KeyboardType.Email) { it.email }
        Field(state, actions, ContactField.WEBSITE, R.string.field_website, R.string.hint_website, Icons.Rounded.Language, KeyboardType.Uri) { it.website }
        Field(state, actions, ContactField.ADDRESS, R.string.field_address, R.string.hint_address, Icons.Rounded.LocationOn, caps = KeyboardCapitalization.Words, singleLine = false) { it.address }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { more = !more }.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.scan_more_details), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(more, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field(state, actions, ContactField.PHONE_ALT, R.string.field_phone_alt, R.string.hint_phone, Icons.Rounded.Call, KeyboardType.Phone) { it.phoneAlt }
                Field(state, actions, ContactField.EMAIL_ALT, R.string.field_email_alt, R.string.hint_email, Icons.Rounded.Email, KeyboardType.Email) { it.emailAlt }
                CategoryPicker(state, actions)
                TagPicker(state, actions)
                Field(state, actions, ContactField.NOTES, R.string.field_notes, R.string.hint_notes, null, caps = KeyboardCapitalization.Sentences, singleLine = false) { it.notes }
            }
        }
        UnmatchedLines(state, actions)
        Spacer(Modifier.height(8.dp))
    }
    if (!state.ocrFailed || manual) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.screen, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            actions.onRescan?.let { VCardlySecondaryButton(stringResource(R.string.scan_rescan), onClick = it, leadingIcon = Icons.Rounded.Refresh, modifier = Modifier.weight(1f)) }
            VCardlyPrimaryButton(
                stringResource(R.string.contact_save_contact),
                onClick = actions.onSave,
                loading = state.isSaving,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

@Composable
private fun SuccessHeader(found: Int) {
    val colors = MaterialTheme.vcColors
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val pop by animateFloatAsState(if (appeared) 1f else 0.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "pop")
        Box(
            Modifier.size(64.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape).background(colors.mint.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(36.dp))
        }
        Text(
            stringResource(R.string.scan_success_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.mint.content,
            modifier = Modifier.padding(top = 12.dp).semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(
            pluralStringResource(R.plurals.scan_success_found, found, found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
