package com.yasin.vcardly.presentation.share

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.qr.QrEncoder
import com.yasin.vcardly.core.qr.QrMatrix
import com.yasin.vcardly.domain.vcard.ShareCard
import com.yasin.vcardly.domain.vcard.ShareField
import com.yasin.vcardly.domain.vcard.VCardWriter

/**
 * Field picker + live QR code + "share as file". Shared by the user's own card and by saved contacts.
 * The QR always encodes exactly the ticked fields, so what you see is what the scanner gets.
 */
@Composable
fun QrSharePanel(card: ShareCard, modifier: Modifier = Modifier, viewModel: VCardShareViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var selected by remember(card) { mutableStateOf(card.defaultSelection) }
    val matrix = remember(card, selected) { QrEncoder.encode(VCardWriter.write(card.toVCard(selected))) }
    var shareFailed by remember { mutableStateOf(false) }
    val chooserTitle = stringResource(R.string.share_chooser_title)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeader(stringResource(R.string.share_choose_fields))
        Text(stringResource(R.string.share_choose_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        card.available.forEach { field ->
            val locked = field == ShareField.NAME
            val checked = field in selected || locked
            Row(
                Modifier.fillMaxWidth().heightIn(min = MaterialTheme.spacing.minTouchTarget)
                    .toggleable(value = checked, enabled = !locked, role = Role.Checkbox) { on ->
                        selected = if (on) selected + field else selected - field
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = !locked)
                Column(Modifier.padding(start = MaterialTheme.spacing.md)) {
                    Text(stringResource(field.labelRes()), style = MaterialTheme.typography.bodyLarge)
                    Text(card.values[field].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.md), contentAlignment = Alignment.Center) {
            if (matrix != null) {
                QrCodeView(matrix, stringResource(R.string.share_qr_description, card.values[ShareField.NAME].orEmpty()))
            } else {
                Text(stringResource(R.string.share_qr_too_large), color = MaterialTheme.colorScheme.error)
            }
        }

        if (shareFailed) Text(stringResource(R.string.share_failed), color = MaterialTheme.colorScheme.error)
        PrimaryButton(
            text = stringResource(R.string.share_as_file),
            onClick = {
                shareFailed = false
                viewModel.share(card, selected, chooserTitle) { intent ->
                    if (intent == null) shareFailed = true else context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Dark modules on a white tile with a 4-module quiet zone, in both themes: scanners need dark-on-light. */
@Composable
fun QrCodeView(matrix: QrMatrix, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val quiet = 4
            val cell = size.width / (matrix.size + quiet * 2)
            for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

@StringRes
fun ShareField.labelRes(): Int = when (this) {
    ShareField.NAME -> R.string.field_full_name
    ShareField.JOB_TITLE -> R.string.field_job_title
    ShareField.COMPANY -> R.string.field_company
    ShareField.PHONE -> R.string.field_phone
    ShareField.PHONE_ALT -> R.string.field_phone_alt
    ShareField.EMAIL -> R.string.field_email
    ShareField.EMAIL_ALT -> R.string.field_email_alt
    ShareField.WEBSITE -> R.string.field_website
    ShareField.ADDRESS -> R.string.field_address
}
