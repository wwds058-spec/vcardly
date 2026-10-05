package com.yasin.vcardly.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.yasin.vcardly.core.designsystem.theme.Contrast
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Initials on the category colour (or primary when uncategorised); the ink colour is chosen per background for contrast.
 */
@Composable
fun ContactAvatar(name: String, categoryColorArgb: Long?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val background = categoryColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    // Black or white, whichever is legible on this particular colour (white fails contrast on orange/yellow).
    val ink = categoryColorArgb?.let { Color(Contrast.readableOn(it)) } ?: MaterialTheme.colorScheme.onPrimary
    Box(
        // Decorative: the name is always rendered next to the avatar, so screen readers skip the initials.
        modifier = modifier.size(size).clip(CircleShape).background(background).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initialsOf(name), color = ink, style = MaterialTheme.typography.titleMedium)
    }
}
