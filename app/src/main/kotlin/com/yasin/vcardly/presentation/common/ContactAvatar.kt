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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Initials on the category colour (or primary when uncategorised). White text is only used on the
 * seeded/palette colours, all of which are dark enough for AA contrast at this size.
 */
@Composable
fun ContactAvatar(name: String, categoryColorArgb: Long?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val background = categoryColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative: the name is always rendered next to the avatar.
        Text(initialsOf(name), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
