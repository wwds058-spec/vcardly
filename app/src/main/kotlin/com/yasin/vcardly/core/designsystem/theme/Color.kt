package com.yasin.vcardly.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand: deep indigo with a teal accent. Pairs below target WCAG AA (4.5:1) for body text.
internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF0B1766),
    secondary = Color(0xFF00695C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2F0E4),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF8E24AA),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7D7FF),
    onTertiaryContainer = Color(0xFF310040),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF14257C),
    primaryContainer = Color(0xFF2D3C93),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFF7FD6C6),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFFB2F0E4),
    tertiary = Color(0xFFEBB2FF),
    onTertiary = Color(0xFF51006B),
    tertiaryContainer = Color(0xFF700091),
    onTertiaryContainer = Color(0xFFF7D7FF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
)
