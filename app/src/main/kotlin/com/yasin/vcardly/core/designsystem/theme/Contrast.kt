package com.yasin.vcardly.core.designsystem.theme

/** WCAG 2.x contrast helpers on plain ARGB values (pure Kotlin, unit-tested). */
object Contrast {
    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L

    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Black or white, whichever reads better on [background]. */
    fun readableOn(background: Long): Long = if (ratio(WHITE, background) >= ratio(BLACK, background)) WHITE else BLACK
}
