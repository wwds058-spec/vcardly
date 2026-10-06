package com.yasin.vcardly.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.component.initialsOf
import com.yasin.vcardly.core.designsystem.theme.vcColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * A visiting card drawn from a contact's own details, shown when no photo of the real card exists. Decorative: the
 * same details are listed (and read by TalkBack) right below it. Like a photo it keeps its shape, so its text follows the
 * user's font size only up to 1.3x; with larger fonts the full details are in the list below.
 */
@Composable
fun BusinessCardArt(
    name: String,
    jobTitle: String,
    company: String,
    phone: String,
    email: String,
    website: String,
    modifier: Modifier = Modifier,
    accentArgb: Long? = null,
) {
    val colors = MaterialTheme.vcColors
    val gradient = accentArgb?.let { listOf(Color(it).copy(alpha = 0.9f), colors.gradientBlue.last(), colors.gradientPurple.last()) } ?: colors.cardHero
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
    Box(
        modifier
            .aspectRatio(1.7f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .clearAndSetSemantics { },
    ) {
        // Soft decorative circles give the card depth without competing with the text.
        Box(Modifier.size(180.dp).offset(x = 200.dp, y = (-70).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Box(Modifier.size(120.dp).offset(x = 250.dp, y = 110.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
                    Text(initialsOf(company.ifBlank { name }).take(2), style = MaterialTheme.typography.titleMedium, color = colors.gradientBlue.last())
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    company.ifBlank { name },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (jobTitle.isNotBlank()) {
                    Text(jobTitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.size(10.dp))
                ArtLine(Icons.Rounded.Call, phone)
                ArtLine(Icons.Rounded.Email, email)
                ArtLine(Icons.Rounded.Language, website)
            }
        }
    }
    }
}

@Composable
private fun ArtLine(icon: ImageVector, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
