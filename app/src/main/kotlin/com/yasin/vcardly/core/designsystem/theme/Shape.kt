package com.yasin.vcardly.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Soft, generous corners. Pills (chips, search, primary buttons) use [PillShape]. */
internal val VCardlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val PillShape = RoundedCornerShape(percent = 50)
val CardShape = RoundedCornerShape(20.dp)
val TileShape = RoundedCornerShape(16.dp)
val IconBadgeShape = RoundedCornerShape(12.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
