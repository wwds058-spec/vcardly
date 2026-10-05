package com.yasin.vcardly.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp grid. Screens use [screen] as their side margin so every page lines up. */
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screen: Dp = 20.dp,
    val card: Dp = 16.dp,
    val section: Dp = 28.dp,
    /** Android's minimum recommended touch target. */
    val minTouchTarget: Dp = 48.dp,
    /** Height of the custom bottom bar, used to pad scrolling content above it. */
    val bottomBar: Dp = 76.dp,
)

/** Elevation tokens: cards are mostly flat with a soft tinted shadow. */
data class Elevation(
    val none: Dp = 0.dp,
    val card: Dp = 6.dp,
    val raised: Dp = 12.dp,
    val floating: Dp = 18.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalElevation = staticCompositionLocalOf { Elevation() }
val LocalVCardlyColors = staticCompositionLocalOf { LightExtended }

val MaterialTheme.spacing: Spacing
    @Composable @ReadOnlyComposable get() = LocalSpacing.current

val MaterialTheme.elevation: Elevation
    @Composable @ReadOnlyComposable get() = LocalElevation.current

val MaterialTheme.vcColors: VCardlyColors
    @Composable @ReadOnlyComposable get() = LocalVCardlyColors.current
