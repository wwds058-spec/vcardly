package com.yasin.vcardly.presentation.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyChip
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.qr.QrEncoder
import com.yasin.vcardly.core.qr.QrMatrix
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.vcard.ShareCard
import com.yasin.vcardly.domain.vcard.ShareField
import com.yasin.vcardly.domain.vcard.VCardWriter

/**
 * "Choose what to share" + live QR + Share / Save QR. Shared by the user's own card and by saved contacts.
 * The QR and the file always contain exactly the selected fields (the name is always included); private notes are
 * never offered.
 */
@Composable
fun QrSharePanel(card: ShareCard, modifier: Modifier = Modifier, viewModel: VCardShareViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_chooser_title)
    var status by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<QrMatrix?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val m = pending
        if (uri != null && m != null) viewModel.saveQr(uri, m) { ok -> status = if (ok) R.string.share_qr_saved else R.string.share_qr_save_failed }
    }
    val fileName = stringResource(R.string.share_qr_file_name)
    QrSharePanelContent(
        card = card,
        status = status,
        onShare = { selected ->
            status = null
            viewModel.share(card, selected, chooserTitle) { intent -> if (intent == null) status = R.string.share_failed else context.startActivity(intent) }
        },
        onSaveQr = { matrix -> status = null; pending = matrix; saveLauncher.launch(fileName) },
        modifier = modifier,
    )
}

/** Stateless panel: selection lives here; sharing and saving are delegated (used directly by UI tests). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QrSharePanelContent(
    card: ShareCard,
    status: Int?,
    onShare: (Set<ShareField>) -> Unit,
    onSaveQr: (QrMatrix) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(card) { mutableStateOf(card.defaultSelection) }
    val matrix = remember(card, selected) { QrEncoder.encode(VCardWriter.write(card.toVCard(selected))) }
    val name = card.values[ShareField.NAME].orEmpty()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.share_qr_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.share_qr_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                AnimatedContent(matrix, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "qr") { m ->
                    if (m != null) {
                        QrCodeView(m, stringResource(R.string.share_qr_description, name), Modifier.widthIn(max = 240.dp))
                    } else {
                        Text(stringResource(R.string.share_qr_too_large), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.share_choose_fields), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.share_choose_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.available.forEach { field ->
                    val locked = field == ShareField.NAME
                    VCardlyChip(
                        label = stringResource(field.labelRes()),
                        selected = field in selected || locked,
                        onClick = { if (!locked) selected = if (field in selected) selected - field else selected + field },
                        leadingIcon = field.icon,
                    )
                }
            }
        }

        status?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == R.string.share_qr_saved) MaterialTheme.vcColors.mint.content else MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VCardlyPrimaryButton(
                text = stringResource(R.string.contact_share),
                onClick = { onShare(selected) },
                leadingIcon = Icons.Rounded.Share,
                containerColor = MaterialTheme.vcColors.gradientBlue.first(),
                modifier = Modifier.weight(1f),
            )
            VCardlyPrimaryButton(
                text = stringResource(R.string.share_save_qr),
                onClick = { matrix?.let(onSaveQr) },
                enabled = matrix != null,
                leadingIcon = Icons.Rounded.Download,
                containerColor = MaterialTheme.vcColors.gradientPurple.first(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Dark modules on a white tile with a 4-module quiet zone, in both themes: scanners need dark-on-light. */
@Composable
fun QrCodeView(matrix: QrMatrix, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val quiet = 4
            val cell = size.width / (matrix.size + quiet * 2)
            for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(Color(0xFF0B1638), Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

@StringRes
fun ShareField.labelRes(): Int = when (this) {
    ShareField.NAME -> R.string.field_name
    ShareField.JOB_TITLE -> R.string.field_designation
    ShareField.COMPANY -> R.string.field_company
    ShareField.PHONE -> R.string.field_phone
    ShareField.PHONE_ALT -> R.string.field_phone_alt
    ShareField.EMAIL -> R.string.field_email
    ShareField.EMAIL_ALT -> R.string.field_email_alt
    ShareField.WEBSITE -> R.string.field_website
    ShareField.ADDRESS -> R.string.field_address
}

private val ShareField.icon: ImageVector
    get() = when (this) {
        ShareField.NAME -> Icons.Rounded.Person
        ShareField.JOB_TITLE -> Icons.Rounded.Badge
        ShareField.COMPANY -> Icons.Rounded.Business
        ShareField.PHONE, ShareField.PHONE_ALT -> Icons.Rounded.Call
        ShareField.EMAIL, ShareField.EMAIL_ALT -> Icons.Rounded.Email
        ShareField.WEBSITE -> Icons.Rounded.Language
        ShareField.ADDRESS -> Icons.Rounded.LocationOn
    }
