package com.yasin.vcardly.core.designsystem.theme

import com.yasin.vcardly.domain.model.CategoryPalette
import com.yasin.vcardly.domain.model.SystemCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test fun knownRatios() {
        assertEquals(21.0, Contrast.ratio(0xFFFFFFFF, 0xFF000000), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF336699, 0xFF336699), 0.0001)
    }

    @Test fun picksTheMoreReadableInk() {
        assertEquals(0xFFFFFFFFL, Contrast.readableOn(0xFF1A237E))   // dark indigo -> white
        assertEquals(0xFF000000L, Contrast.readableOn(0xFFFFEB3B))   // yellow -> black
    }

    @Test fun avatarTextIsLegibleOnEveryBuiltInAndPaletteColour() {
        // Large-ish bold text needs 3:1 (WCAG AA large); the chosen ink must reach it for every colour the app can assign.
        val colours = SystemCategory.entries.map { it.colorArgb } + CategoryPalette.colors
        colours.forEach { c ->
            val ink = Contrast.readableOn(c)
            assertTrue("colour %08X ratio %.2f".format(c, Contrast.ratio(ink, c)), Contrast.ratio(ink, c) >= 3.0)
        }
    }
}
