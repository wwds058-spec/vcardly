package com.yasin.vcardly.presentation.contacts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.TimelineEntry
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyChip
import com.yasin.vcardly.core.designsystem.component.VCardlyChipRow
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyGroup
import com.yasin.vcardly.core.designsystem.component.VCardlyIconButton
import com.yasin.vcardly.core.designsystem.component.VCardlyInfoRow
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlySectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTag
import com.yasin.vcardly.core.designsystem.component.VCardlyTimeline
import com.yasin.vcardly.core.designsystem.component.VCardlyTonalButton
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.core.image.CardImageRef
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.presentation.common.BusinessCardArt
import com.yasin.vcardly.presentation.common.CardImageView
import com.yasin.vcardly.presentation.common.CardImageViewer
import com.yasin.vcardly.presentation.common.ExternalActions
import com.yasin.vcardly.presentation.common.FavoriteButton
import com.yasin.vcardly.presentation.common.VCardlyFollowUpCard
import com.yasin.vcardly.presentation.common.displayName
import com.yasin.vcardly.presentation.common.whatsAppDigits
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

enum class DetailTab { DETAILS, CARD_IMAGES, NOTES, FOLLOW_UPS }

data class ContactDetailActions(
    val onNavigateUp: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onAddFollowUp: () -> Unit = {},
    val onOpenFollowUp: (Long) -> Unit = {},
    val onToggleFollowUp: (FollowUp) -> Unit = {},
)

@Composable
fun ContactDetailScreen(
    onNavigateUp: () -> Unit,
    onEdit: (Long) -> Unit,
    onShare: (Long) -> Unit,
    onAddFollowUp: (contactId: Long) -> Unit,
    onOpenFollowUp: (Long) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val followUps by viewModel.followUps.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val content = state as? ContactDetailUiState.Content

    ContactDetailContent(
        state = state,
        followUps = followUps,
        actions = ContactDetailActions(
            onNavigateUp = onNavigateUp,
            onEdit = { content?.let { onEdit(it.details.contact.id) } },
            onShare = { content?.let { onShare(it.details.contact.id) } },
            onDelete = { confirmDelete = true },
            onToggleFavorite = viewModel::toggleFavorite,
            onAddFollowUp = { content?.let { onAddFollowUp(it.details.contact.id) } },
            onOpenFollowUp = onOpenFollowUp,
            onToggleFollowUp = { f -> if (f.status.isActive) viewModel.completeFollowUp(f.id) else viewModel.reopenFollowUp(f.id) },
        ),
    )

    if (confirmDelete && content != null) {
        ConfirmDialog(
            title = stringResource(R.string.contact_delete_title),
            message = stringResource(R.string.contact_delete_message, content.details.contact.fullName),
            confirmText = stringResource(R.string.contact_delete),
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDone = onNavigateUp)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Stateless details content (used directly by UI tests). */
@Composable
fun ContactDetailContent(
    state: ContactDetailUiState,
    followUps: List<FollowUp>,
    actions: ContactDetailActions,
    initialTab: DetailTab = DetailTab.DETAILS,
) {
    when (state) {
        ContactDetailUiState.Loading -> Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { VCardlyLoadingState(kind = SkeletonKind.DETAIL) }
        ContactDetailUiState.NotFound -> Column {
            com.yasin.vcardly.core.designsystem.component.VCardlyTopBar(title = "", onNavigateUp = actions.onNavigateUp)
            VCardlyEmptyState(
                icon = Icons.Rounded.PersonOff,
                title = stringResource(R.string.contact_not_found_title),
                message = stringResource(R.string.contact_not_found_message),
            )
        }
        is ContactDetailUiState.Content -> Loaded(state.details, followUps, actions, initialTab)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Loaded(details: ContactDetails, followUps: List<FollowUp>, actions: ContactDetailActions, initialTab: DetailTab) {
    val contact = details.contact
    val context = LocalContext.current
    val colors = MaterialTheme.vcColors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var viewer by remember { mutableStateOf<Pair<CardImageRef, String>?>(null) }
    val copiedMessage = stringResource(R.string.common_copied)
    val noAppMessage = stringResource(R.string.common_no_app)
    val copy: (String, String) -> Unit = { label, value ->
        // Android 13+ shows its own confirmation; older versions get a snackbar.
        if (!ExternalActions.copy(context, label, value)) scope.launch { snackbar.showSnackbar(copiedMessage) }
    }
    val launch: (Boolean) -> Unit = { ok -> if (!ok) scope.launch { snackbar.showSnackbar(noAppMessage) } }
    val phone = contact.phone.ifBlank { contact.phoneAlt }
    val email = contact.email.ifBlank { contact.emailAlt }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Hero(details, actions, onOpenImage = { ref, label -> viewer = ref to label })

            // Content sheet overlapping the hero.
            Column(
                Modifier
                    .offset(y = (-22).dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = MaterialTheme.spacing.screen)
                    .padding(top = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.fullName,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                    )
                    details.category?.let {
                        Spacer(Modifier.size(10.dp))
                        VCardlyTag(it.displayName().asString(), Tone(Color(it.colorArgb).copy(alpha = 0.14f), MaterialTheme.colorScheme.onSurface, Color(it.colorArgb)))
                    }
                }
                if (contact.jobTitle.isNotBlank()) {
                    Text(contact.jobTitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (contact.company.isNotBlank()) {
                    Text(contact.company, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }

                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickAction(Icons.Rounded.Call, stringResource(R.string.action_call), colors.blue, enabled = phone.isNotBlank()) { launch(ExternalActions.dial(context, phone)) }
                    QuickAction(Icons.AutoMirrored.Rounded.Chat, stringResource(R.string.followup_type_whatsapp), colors.mint, enabled = whatsAppDigits(phone) != null) { launch(ExternalActions.whatsApp(context, phone)) }
                    QuickAction(Icons.Rounded.Email, stringResource(R.string.field_email), colors.lavender, enabled = email.isNotBlank()) { launch(ExternalActions.email(context, email)) }
                    MoreAction(actions)
                }

                val followUpCount = followUps.count { it.status.isActive }
                VCardlyChipRow(Modifier.padding(top = 20.dp, bottom = 16.dp), horizontalPadding = 0.dp) {
                    item { VCardlyChip(stringResource(R.string.detail_tab_details), tab == DetailTab.DETAILS, { tab = DetailTab.DETAILS }) }
                    item { VCardlyChip(stringResource(R.string.detail_tab_card_images), tab == DetailTab.CARD_IMAGES, { tab = DetailTab.CARD_IMAGES }) }
                    item { VCardlyChip(stringResource(R.string.field_notes), tab == DetailTab.NOTES, { tab = DetailTab.NOTES }) }
                    item {
                        VCardlyChip(
                            if (followUpCount > 0) stringResource(R.string.detail_tab_follow_ups_count, followUpCount) else stringResource(R.string.nav_follow_ups),
                            tab == DetailTab.FOLLOW_UPS,
                            { tab = DetailTab.FOLLOW_UPS },
                        )
                    }
                }

                AnimatedContent(tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "detailTab") { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (current) {
                            DetailTab.DETAILS -> DetailsTab(details, followUps, copy, launch)
                            DetailTab.CARD_IMAGES -> CardImagesTab(details, actions.onEdit) { ref, label -> viewer = ref to label }
                            DetailTab.NOTES -> NotesTab(contact.notes, actions.onEdit)
                            DetailTab.FOLLOW_UPS -> FollowUpsTab(details, followUps, actions)
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    viewer?.let { (ref, label) -> CardImageViewer(ref, label, onDismiss = { viewer = null }) }
}

/** Large card image (or a generated card) on a deep backdrop, with back / favourite / share overlaid. */
@Composable
private fun Hero(details: ContactDetails, actions: ContactDetailActions, onOpenImage: (CardImageRef, String) -> Unit) {
    val contact = details.contact
    val frontLabel = stringResource(R.string.card_front)
    val front = contact.frontImagePath?.let { CardImageRef.Stored(it) }
    val overlay = Color.White.copy(alpha = 0.16f)
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0B1638), Color(0xFF1B2D6B))))) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp, bottom = 52.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cardModifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .rotate(-3f)
                .shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
            if (front != null) {
                CardImageView(
                    front,
                    frontLabel,
                    cardModifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.detail_zoom_image)) { onOpenImage(front, frontLabel) },
                    contentScale = ContentScale.Crop,
                    maxDimension = 1400,
                )
            } else {
                BusinessCardArt(contact.fullName, contact.jobTitle, contact.company, contact.phone, contact.email, contact.website, cardModifier, details.category?.colorArgb)
            }
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VCardlyIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(R.string.common_navigate_up),
                actions.onNavigateUp,
                containerColor = overlay,
                contentColor = Color.White,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(overlay))
                FavoriteButton(contact.isFavorite, contact.fullName, actions.onToggleFavorite, tint = Color.White)
            }
            VCardlyIconButton(Icons.Rounded.QrCode2, stringResource(R.string.share_title), actions.onShare, containerColor = overlay, contentColor = Color.White)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tone: Tone, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (enabled) tone.container else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) tone.accent else MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MoreAction(actions: ContactDetailActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        QuickAction(Icons.Rounded.MoreHoriz, stringResource(R.string.common_more), MaterialTheme.vcColors.navy, enabled = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.share_title)) }, leadingIcon = { Icon(Icons.Rounded.QrCode2, null) }, onClick = { open = false; actions.onShare() })
            DropdownMenuItem(text = { Text(stringResource(R.string.contact_edit)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { open = false; actions.onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.followup_add)) }, leadingIcon = { Icon(Icons.Rounded.PostAdd, null) }, onClick = { open = false; actions.onAddFollowUp() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contact_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; actions.onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsTab(details: ContactDetails, followUps: List<FollowUp>, copy: (String, String) -> Unit, launch: (Boolean) -> Unit) {
    val contact = details.contact
    val context = LocalContext.current
    val colors = MaterialTheme.vcColors
    val rows = buildList<@Composable () -> Unit> {
        fun phoneRow(labelRes: Int, value: String) = add {
            val label = stringResource(labelRes)
            VCardlyInfoRow(Icons.Rounded.Call, colors.blue, label, value, onClick = { launch(ExternalActions.dial(context, value)) }, onClickLabel = stringResource(R.string.action_call),
                trailingIcon = Icons.Rounded.ContentCopy, trailingDescription = stringResource(R.string.common_copy_item, label), onTrailing = { copy(label, value) })
        }
        if (contact.phone.isNotBlank()) phoneRow(R.string.field_phone, contact.phone)
        if (contact.phoneAlt.isNotBlank()) phoneRow(R.string.field_phone_alt, contact.phoneAlt)
        val wa = contact.phone.ifBlank { contact.phoneAlt }
        if (whatsAppDigits(wa) != null) add {
            val label = stringResource(R.string.followup_type_whatsapp)
            VCardlyInfoRow(Icons.AutoMirrored.Rounded.Chat, colors.mint, label, wa, onClick = { launch(ExternalActions.whatsApp(context, wa)) }, onClickLabel = stringResource(R.string.detail_open_whatsapp),
                trailingIcon = Icons.Rounded.ContentCopy, trailingDescription = stringResource(R.string.common_copy_item, label), onTrailing = { copy(label, wa) })
        }
        fun emailRow(labelRes: Int, value: String) = add {
            val label = stringResource(labelRes)
            VCardlyInfoRow(Icons.Rounded.Email, colors.lavender, label, value, onClick = { launch(ExternalActions.email(context, value)) }, onClickLabel = stringResource(R.string.action_email),
                trailingIcon = Icons.Rounded.ContentCopy, trailingDescription = stringResource(R.string.common_copy_item, label), onTrailing = { copy(label, value) })
        }
        if (contact.email.isNotBlank()) emailRow(R.string.field_email, contact.email)
        if (contact.emailAlt.isNotBlank()) emailRow(R.string.field_email_alt, contact.emailAlt)
        if (contact.website.isNotBlank()) add {
            val label = stringResource(R.string.field_website)
            VCardlyInfoRow(Icons.Rounded.Language, colors.blue, label, contact.website, onClick = { launch(ExternalActions.website(context, contact.website)) }, onClickLabel = stringResource(R.string.detail_open_website),
                trailingIcon = Icons.Rounded.ContentCopy, trailingDescription = stringResource(R.string.common_copy_item, label), onTrailing = { copy(label, contact.website) })
        }
        if (contact.address.isNotBlank()) add {
            val label = stringResource(R.string.field_address)
            VCardlyInfoRow(Icons.Rounded.LocationOn, colors.orange, label, contact.address, onClick = { launch(ExternalActions.map(context, contact.address)) }, onClickLabel = stringResource(R.string.detail_open_map),
                trailingIcon = Icons.Rounded.ContentCopy, trailingDescription = stringResource(R.string.common_copy_item, label), onTrailing = { copy(label, contact.address) })
        }
    }
    if (rows.isNotEmpty()) {
        VCardlyGroup { rows.forEach { it() } }
    } else {
        Text(stringResource(R.string.detail_no_contact_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (details.tags.isNotEmpty()) {
        VCardlySectionHeader(stringResource(R.string.field_tags))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            details.tags.forEach { VCardlyTag("#" + it.name, colors.blue) }
        }
    }
    VCardlySectionHeader(stringResource(R.string.detail_activity))
    VCardlyCard(Modifier.fillMaxWidth()) {
        VCardlyTimeline(activityFor(details, followUps))
    }
}

/** Activity derived from stored timestamps only (nothing is invented): creation, edits and follow-up events. */
@Composable
private fun activityFor(details: ContactDetails, followUps: List<FollowUp>): List<TimelineEntry> {
    val colors = MaterialTheme.vcColors
    val res = LocalContext.current.resources
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val contact = details.contact
    val events = mutableListOf<Pair<Long, (String) -> TimelineEntry>>()
    events += contact.createdAt to { time: String ->
        val (icon, text) = when (contact.source) {
            ContactSource.SCAN -> Icons.Rounded.AddPhotoAlternate to R.string.activity_created_scan
            ContactSource.IMPORT -> Icons.Rounded.PersonAdd to R.string.activity_created_import
            ContactSource.MANUAL -> Icons.Rounded.PersonAdd to R.string.activity_created_manual
        }
        TimelineEntry(icon, colors.blue, res.getString(text), time)
    }
    if (contact.updatedAt - contact.createdAt > 60_000) {
        events += contact.updatedAt to { time: String -> TimelineEntry(Icons.Rounded.Update, colors.lavender, res.getString(R.string.activity_updated), time) }
    }
    followUps.forEach { f ->
        events += f.createdAt to { time: String -> TimelineEntry(Icons.Rounded.EventAvailable, colors.orange, res.getString(R.string.activity_followup_created, f.title), time) }
        val done = f.completedAt
        if (f.status == FollowUpStatus.COMPLETED && done != null) {
            events += done to { time: String -> TimelineEntry(Icons.Rounded.CheckCircle, colors.mint, res.getString(R.string.activity_followup_completed, f.title), time) }
        }
        if (f.status == FollowUpStatus.CANCELLED) {
            events += f.updatedAt to { time: String -> TimelineEntry(Icons.Rounded.Cancel, colors.rose, res.getString(R.string.activity_followup_cancelled, f.title), time) }
        }
    }
    return events.sortedByDescending { it.first }.take(MAX_ACTIVITY).map { (at, make) -> make(formatter.format(Date(at))) }
}

private const val MAX_ACTIVITY = 8

@Composable
private fun CardImagesTab(details: ContactDetails, onEdit: () -> Unit, onOpen: (CardImageRef, String) -> Unit) {
    val contact = details.contact
    val images = listOfNotNull(
        contact.frontImagePath?.let { CardImageRef.Stored(it) to stringResource(R.string.card_front) },
        contact.backImagePath?.let { CardImageRef.Stored(it) to stringResource(R.string.card_back) },
    )
    if (images.isEmpty()) {
        VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 24.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MaterialTheme.vcColors.blue.accent, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.detail_no_cards_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                Text(stringResource(R.string.detail_no_cards_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                VCardlyTonalButton(stringResource(R.string.detail_add_card_images), onClick = onEdit, leadingIcon = Icons.Rounded.Add)
            }
        }
        return
    }
    images.forEach { (ref, label) ->
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                VCardlyIconButton(Icons.Rounded.ZoomIn, stringResource(R.string.detail_zoom_item, label), { onOpen(ref, label) }, size = 36.dp)
            }
            CardImageView(
                ref,
                label,
                Modifier.fillMaxWidth().padding(top = 6.dp).clickable(role = Role.Button, onClickLabel = stringResource(R.string.detail_zoom_image)) { onOpen(ref, label) },
                maxDimension = 1400,
            )
        }
    }
    VCardlyTonalButton(stringResource(R.string.detail_manage_card_images), onClick = onEdit, leadingIcon = Icons.Rounded.Edit, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NotesTab(notes: String, onEdit: () -> Unit) {
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.vcColors.lavender.accent)
            Text(stringResource(R.string.field_notes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
            VCardlyIconButton(Icons.Rounded.Edit, stringResource(R.string.detail_edit_notes), onEdit, size = 36.dp)
        }
        Text(
            notes.ifBlank { stringResource(R.string.detail_no_notes) },
            style = MaterialTheme.typography.bodyLarge,
            color = if (notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun FollowUpsTab(details: ContactDetails, followUps: List<FollowUp>, actions: ContactDetailActions) {
    val startOfToday = remember { java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    if (followUps.isEmpty()) {
        Text(stringResource(R.string.followup_none_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.detail_followups_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    followUps.forEach { f ->
        VCardlyFollowUpCard(
            item = FollowUpWithContact(f, details.contact.fullName, details.contact.company),
            isOverdue = f.dueAt < startOfToday,
            onClick = { actions.onOpenFollowUp(f.id) },
            onToggleDone = { actions.onToggleFollowUp(f) },
        )
    }
    VCardlyTonalButton(stringResource(R.string.followup_add), onClick = actions.onAddFollowUp, leadingIcon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
}
