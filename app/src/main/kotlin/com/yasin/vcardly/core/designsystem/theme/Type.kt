package com.yasin.vcardly.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yasin.vcardly.R

/**
 * Plus Jakarta Sans (SIL OFL 1.1, bundled in res/font; licence in assets/licenses). It only covers Latin scripts:
 * Android falls back to the system font per glyph, so Telugu, Devanagari and Urdu text still renders correctly.
 * All sizes are in sp so they follow the user's font scale.
 */
val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/**
 * Hierarchy: large for screen titles, names and key numbers; medium for section titles; small for metadata.
 * Bold is reserved for titles and numbers; body text stays regular.
 */
internal val VCardlyTypography = Typography(
    displaySmall = style(34, 42, FontWeight.Bold, -0.6),
    headlineLarge = style(30, 38, FontWeight.Bold, -0.5),
    headlineMedium = style(26, 34, FontWeight.Bold, -0.4),
    headlineSmall = style(22, 30, FontWeight.Bold, -0.2),
    titleLarge = style(20, 28, FontWeight.SemiBold, -0.1),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium, 0.2),
)

/** Big figures on stat cards and reports. */
val StatValueStyle = style(28, 34, FontWeight.Bold, -0.6)

/** Small upper section labels ("QUICK ACTIONS"). */
val OverlineStyle = style(12, 16, FontWeight.SemiBold, 0.8)
