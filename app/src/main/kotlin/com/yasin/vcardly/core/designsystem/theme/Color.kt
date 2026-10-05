package com.yasin.vcardly.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * VCardly brand palette. Deep professional blue carries the brand; soft blue, lavender, mint, orange and rose are
 * accents used sparingly (stat cards, quick actions, status). Text/background pairs are checked against WCAG AA in
 * ThemeContrastTest, so change a value there too if you change it here.
 */
object Brand {
    val Navy = Color(0xFF14276B)
    val NavyDeep = Color(0xFF0B1638)
    val Blue = Color(0xFF2456F0)
    val BlueBright = Color(0xFF3B6FFF)
    val SoftBlue = Color(0xFFE8EEFF)
    val Lavender = Color(0xFF7B5CF5)
    val Mint = Color(0xFF16A36A)
    val Orange = Color(0xFFF07A2B)
    val Rose = Color(0xFFEC4468)
}

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2456F0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5ECFF),
    onPrimaryContainer = Color(0xFF0B2A86),
    inversePrimary = Color(0xFF9DB6FF),
    secondary = Color(0xFF14276B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE4F7),
    onSecondaryContainer = Color(0xFF0E1C4F),
    tertiary = Color(0xFF6A47E8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE7FF),
    onTertiaryContainer = Color(0xFF2B1477),
    error = Color(0xFFC62042),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE4E9),
    onErrorContainer = Color(0xFF5C0A1D),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF5F7FC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFF545E73),
    surfaceTint = Color(0xFF2456F0),
    inverseSurface = Color(0xFF1C2438),
    inverseOnSurface = Color(0xFFEFF2FA),
    outline = Color(0xFF8A93A8),
    outlineVariant = Color(0xFFE1E6F0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6EAF3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F3FA),
    surfaceContainerHighest = Color(0xFFE9EDF6),
)

/** Deep navy, designed for the dark (not an inversion of the light theme). */
internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FAEFF),
    onPrimary = Color(0xFF06194F),
    primaryContainer = Color(0xFF1E3B95),
    onPrimaryContainer = Color(0xFFDCE5FF),
    inversePrimary = Color(0xFF2456F0),
    secondary = Color(0xFFB8C6F0),
    onSecondary = Color(0xFF0E1C4F),
    secondaryContainer = Color(0xFF243566),
    onSecondaryContainer = Color(0xFFDDE4F7),
    tertiary = Color(0xFFB9A6FF),
    onTertiary = Color(0xFF2B1477),
    tertiaryContainer = Color(0xFF3F2A9C),
    onTertiaryContainer = Color(0xFFEDE7FF),
    error = Color(0xFFFF8FA5),
    onError = Color(0xFF5C0A1D),
    errorContainer = Color(0xFF7A1630),
    onErrorContainer = Color(0xFFFFE4E9),
    background = Color(0xFF081230),
    onBackground = Color(0xFFE7ECF8),
    surface = Color(0xFF081230),
    onSurface = Color(0xFFE7ECF8),
    surfaceVariant = Color(0xFF16244B),
    onSurfaceVariant = Color(0xFFA9B4D0),
    surfaceTint = Color(0xFF8FAEFF),
    inverseSurface = Color(0xFFE7ECF8),
    inverseOnSurface = Color(0xFF16213F),
    outline = Color(0xFF6B78A0),
    outlineVariant = Color(0xFF223463),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1C2B57),
    surfaceDim = Color(0xFF060E26),
    surfaceContainerLowest = Color(0xFF060E26),
    surfaceContainerLow = Color(0xFF0E1A3C),
    surfaceContainer = Color(0xFF111F45),
    surfaceContainerHigh = Color(0xFF17264F),
    surfaceContainerHighest = Color(0xFF1D2D5A),
)

/** One accent family: a tinted container, the ink used for text on it, and a bright accent for icons and charts. */
data class Tone(val container: Color, val content: Color, val accent: Color)

/** Colours beyond Material's scheme. Read through [MaterialTheme.vcColors]. */
data class VCardlyColors(
    val blue: Tone,
    val rose: Tone,
    val mint: Tone,
    val orange: Tone,
    val lavender: Tone,
    val navy: Tone,
    /** Strong call-to-action fill (Get started, Next, Save). */
    val cta: Color,
    val onCta: Color,
    val favorite: Color,
    val whatsapp: Color,
    /** Gradient stops for the quick-action tiles and the digital card. White text on every stop is checked. */
    val gradientBlue: List<Color>,
    val gradientIndigo: List<Color>,
    val gradientPurple: List<Color>,
    val gradientOrange: List<Color>,
    val cardHero: List<Color>,
    val shadow: Color,
    val isDark: Boolean,
)

internal val LightExtended = VCardlyColors(
    blue = Tone(Color(0xFFEAF0FF), Color(0xFF1D46C7), Color(0xFF2F68FF)),
    rose = Tone(Color(0xFFFFEDF1), Color(0xFFB4163C), Color(0xFFF0466B)),
    mint = Tone(Color(0xFFE6F7EE), Color(0xFF0E7342), Color(0xFF19B36B)),
    orange = Tone(Color(0xFFFFF0E5), Color(0xFFAD4A08), Color(0xFFF27A1A)),
    lavender = Tone(Color(0xFFF0EBFF), Color(0xFF5235C9), Color(0xFF7B5CF5)),
    navy = Tone(Color(0xFFE6EAF5), Color(0xFF14276B), Color(0xFF14276B)),
    cta = Color(0xFF14276B),
    onCta = Color(0xFFFFFFFF),
    favorite = Color(0xFFE09400),
    whatsapp = Color(0xFF128C4B),
    gradientBlue = listOf(Color(0xFF2F63F5), Color(0xFF1D46C7)),
    gradientIndigo = listOf(Color(0xFF3A5BE0), Color(0xFF223BA8)),
    gradientPurple = listOf(Color(0xFF7A55EE), Color(0xFF5530C4)),
    gradientOrange = listOf(Color(0xFFC9480A), Color(0xFFA83A05)),
    cardHero = listOf(Color(0xFF3B6FFF), Color(0xFF2747C9), Color(0xFF5B3CD6)),
    shadow = Color(0xFF1B3A8A),
    isDark = false,
)

internal val DarkExtended = VCardlyColors(
    blue = Tone(Color(0xFF14275C), Color(0xFFB9CBFF), Color(0xFF7FA2FF)),
    rose = Tone(Color(0xFF3A1730), Color(0xFFFFB3C3), Color(0xFFFF7896)),
    mint = Tone(Color(0xFF0F3533), Color(0xFF9EEBC4), Color(0xFF3FD48F)),
    orange = Tone(Color(0xFF3B2419), Color(0xFFFFC79E), Color(0xFFFF9C52)),
    lavender = Tone(Color(0xFF261E55), Color(0xFFD3C6FF), Color(0xFFA48CFF)),
    navy = Tone(Color(0xFF17264F), Color(0xFFDCE5FF), Color(0xFF8FAEFF)),
    cta = Color(0xFF2F5FEA),
    onCta = Color(0xFFFFFFFF),
    favorite = Color(0xFFFFC233),
    whatsapp = Color(0xFF3FD48F),
    gradientBlue = listOf(Color(0xFF2F63F5), Color(0xFF1D46C7)),
    gradientIndigo = listOf(Color(0xFF3A5BE0), Color(0xFF223BA8)),
    gradientPurple = listOf(Color(0xFF7A55EE), Color(0xFF5530C4)),
    gradientOrange = listOf(Color(0xFFC9480A), Color(0xFFA83A05)),
    cardHero = listOf(Color(0xFF3463F0), Color(0xFF1F3BA8), Color(0xFF4B2FB8)),
    shadow = Color(0xFF000000),
    isDark = true,
)
