package com.yasin.vcardly.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.component.VCardlyAvatar
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.MyCard

/**
 * The user's own digital business card: gradient card, large initials portrait, name, designation, company and a row
 * of channel badges (only for details that exist). Text is announced as one item.
 */
@Composable
fun VCardlyDigitalCard(card: MyCard, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.vcColors
    Box(
        modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(colors.cardHero)),
    ) {
        // Decorative light blobs.
        Box(Modifier.size(220.dp).offset(x = (-80).dp, y = (-90).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Box(Modifier.size(160.dp).align(Alignment.BottomEnd).offset(x = 50.dp, y = 50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(104.dp).clip(CircleShape).border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
                VCardlyAvatar(card.fullName, 0xFFE8EEFF, size = 96.dp)
            }
            Column(Modifier.semantics(mergeDescendants = true) {}.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.fullName, style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.jobTitle.isNotBlank()) {
                    Text(card.jobTitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f), textAlign = TextAlign.Center)
                }
                if (card.company.isNotBlank()) {
                    Text(card.company, style = MaterialTheme.typography.titleSmall, color = Color.White, textAlign = TextAlign.Center)
                }
            }
            val phone = card.phone.ifBlank { card.phoneAlt }
            val badges = buildList {
                if (phone.isNotBlank()) add(Icons.Rounded.Call to colors.blue.accent)
                if (whatsAppDigits(phone) != null) add(Icons.Rounded.Chat to colors.whatsapp)
                if (card.email.isNotBlank() || card.emailAlt.isNotBlank()) add(Icons.Rounded.Email to colors.lavender.accent)
                if (card.website.isNotBlank()) add(Icons.Rounded.Language to colors.blue.accent)
                if (card.address.isNotBlank()) add(Icons.Rounded.LocationOn to colors.rose.accent)
            }
            if (badges.isNotEmpty()) {
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    badges.forEach { (icon, tint) -> ChannelBadge(icon, tint) }
                }
            }
        }
    }
}

@Composable
private fun ChannelBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
