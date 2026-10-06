package com.yasin.vcardly.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle

/** Font scale from which layouts adapt: text that is cut to one line at the default size wraps, side-by-side items stack. */
const val LargeTextScale = 1.5f

/** True when the user picked a very large font size in system settings (Android 14 goes up to 2x). */
@Composable
@ReadOnlyComposable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LargeTextScale

/**
 * [style] for text in a slot that cannot grow (bottom-navigation labels): it follows the user's font size up to [max]x
 * and no further. Only for short labels that TalkBack also reads in full.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.cappedScale(max: Float): TextStyle {
    val scale = LocalDensity.current.fontScale
    if (scale <= max) return this
    val factor = max / scale
    return copy(
        fontSize = if (fontSize.isSp) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSp) lineHeight * factor else lineHeight,
    )
}
