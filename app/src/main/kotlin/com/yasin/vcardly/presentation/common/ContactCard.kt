package com.yasin.vcardly.presentation.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyAvatar
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyIconButton
import com.yasin.vcardly.core.designsystem.theme.isLargeText
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.ContactDetails

/** Star that springs when toggled. TalkBack hears "Add/Remove <name> to/from favorites". */
@Composable
fun FavoriteButton(isFavorite: Boolean, name: String, onToggle: () -> Unit, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color? = null) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "favoriteScale",
    )
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = stringResource(if (isFavorite) R.string.contact_remove_favorite else R.string.contact_add_favorite, name),
            tint = if (isFavorite) MaterialTheme.vcColors.favorite else tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

/**
 * Contact list card: avatar, name, company and designation, a favourite star, and compact Call / WhatsApp / Email
 * shortcuts for the channels this contact actually has.
 */
@Composable
fun VCardlyContactCard(
    details: ContactDetails,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
) {
    val contact = details.contact
    val context = LocalContext.current
    VCardlyCard(modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.action_open_contact), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VCardlyAvatar(contact.fullName, details.category?.colorArgb, size = 52.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp).semantics(mergeDescendants = true) {}) {
                Text(contact.fullName, style = MaterialTheme.typography.titleMedium, maxLines = if (isLargeText()) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                if (contact.company.isNotBlank()) {
                    Text(contact.company, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (isLargeText()) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                }
                if (contact.jobTitle.isNotBlank()) {
                    Text(contact.jobTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (isLargeText()) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FavoriteButton(contact.isFavorite, contact.fullName, onToggleFavorite)
        }
        val phone = contact.phone.ifBlank { contact.phoneAlt }
        val email = contact.email.ifBlank { contact.emailAlt }
        if (showActions && (phone.isNotBlank() || email.isNotBlank())) {
            Row(Modifier.padding(start = 58.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val colors = MaterialTheme.vcColors
                if (phone.isNotBlank()) {
                    VCardlyIconButton(Icons.Rounded.Call, stringResource(R.string.action_call_name, contact.fullName), { ExternalActions.dial(context, phone) },
                        containerColor = colors.blue.container, contentColor = colors.blue.accent, size = 36.dp)
                    if (whatsAppDigits(phone) != null) {
                        VCardlyIconButton(Icons.AutoMirrored.Rounded.Chat, stringResource(R.string.action_whatsapp_name, contact.fullName), { ExternalActions.whatsApp(context, phone) },
                            containerColor = colors.mint.container, contentColor = colors.whatsapp, size = 36.dp)
                    }
                }
                if (email.isNotBlank()) {
                    VCardlyIconButton(Icons.Rounded.Email, stringResource(R.string.action_email_name, contact.fullName), { ExternalActions.email(context, email) },
                        containerColor = colors.lavender.container, contentColor = colors.lavender.accent, size = 36.dp)
                }
            }
        }
    }
}

/** Compact tile for horizontal "Recent contacts" carousels. */
@Composable
fun VCardlyContactMini(details: ContactDetails, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val contact = details.contact
    VCardlyCard(modifier.width(132.dp), onClick = onClick, onClickLabel = stringResource(R.string.action_open_contact), contentPadding = 14.dp) {
        VCardlyAvatar(contact.fullName, details.category?.colorArgb, size = 44.dp)
        Spacer(Modifier.size(10.dp))
        Text(contact.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            contact.company.ifBlank { contact.jobTitle }.ifBlank { " " },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
