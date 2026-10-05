package com.yasin.vcardly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yasin.vcardly.core.designsystem.theme.Contrast
import com.yasin.vcardly.core.designsystem.theme.Jakarta
import androidx.compose.ui.text.font.FontWeight

/**
 * Initials avatar on a soft gradient of the contact's category colour (brand blue when uncategorised). The ink is black
 * or white, whichever passes contrast on that colour. Decorative: the name is always shown next to it.
 */
@Composable
fun VCardlyAvatar(name: String, colorArgb: Long?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val baseArgb = colorArgb ?: 0xFF2456F0
    val base = Color(baseArgb)
    val ink = Color(Contrast.readableOn(baseArgb))
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.92f).compositeOverWhite(), base)))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            color = ink,
            style = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.36f).sp),
        )
    }
}

private fun Color.compositeOverWhite(): Color = Color(
    red = red * alpha + (1 - alpha),
    green = green * alpha + (1 - alpha),
    blue = blue * alpha + (1 - alpha),
)
