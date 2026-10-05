package com.yasin.vcardly.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text/background pairs used by the design system must meet WCAG AA (4.5:1) in both themes. */
class ThemeContrastTest {
    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

    private fun check(name: String, fg: Color, bg: Color, min: Double = 4.5) {
        val r = Contrast.ratio(fg.argb(), bg.argb())
        assertTrue("$name contrast ${"%.2f".format(r)} < $min", r >= min)
    }

    @Test fun schemeText() {
        for ((mode, c) in listOf("light" to LightColors, "dark" to DarkColors)) {
            check("$mode onBackground", c.onBackground, c.background)
            check("$mode onSurfaceVariant/background", c.onSurfaceVariant, c.background)
            check("$mode onSurfaceVariant/card", c.onSurfaceVariant, c.surfaceContainer)
            check("$mode primary on card", c.primary, c.surfaceContainer)
            check("$mode onPrimary", c.onPrimary, c.primary)
            check("$mode onPrimaryContainer", c.onPrimaryContainer, c.primaryContainer)
            check("$mode error on card", c.error, c.surfaceContainer)
        }
    }

    @Test fun toneInkOnTint() {
        for ((mode, x) in listOf("light" to LightExtended, "dark" to DarkExtended)) {
            listOf("blue" to x.blue, "rose" to x.rose, "mint" to x.mint, "orange" to x.orange, "lavender" to x.lavender, "navy" to x.navy)
                .forEach { (n, t) -> check("$mode $n ink", t.content, t.container) }
            check("$mode cta", x.onCta, x.cta)
        }
    }

    /** White labels sit on the gradient tiles; every stop must carry them (large-ish bold text, so 3:1 floor, aim 4.5). */
    @Test fun whiteOnGradients() {
        for (x in listOf(LightExtended, DarkExtended)) {
            (x.gradientBlue + x.gradientIndigo + x.gradientPurple + x.gradientOrange).forEach { check("white on $it", Color.White, it) }
        }
    }
}
