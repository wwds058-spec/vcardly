package com.yasin.vcardly.domain.scan

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardEdgeDetectorTest {

    /** A synthetic photo: a background with sensor noise, a card (optionally tilted) and dark "text" bars on the card. */
    private class Scene(
        val width: Int = 320,
        val height: Int = 240,
        val background: Int = 60,
        val card: Int = 225,
        val cx: Float = 160f,
        val cy: Float = 120f,
        val cardW: Float = 210f,
        val cardH: Float = 124f,
        val degrees: Float = 0f,
        val noise: Int = 8,
        val text: Boolean = true,
        val extra: (x: Int, y: Int) -> Int? = { _, _ -> null },
    ) {
        fun render(): GrayImage {
            val random = Random(42)
            val rad = Math.toRadians(degrees.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val pixels = IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                // Position in the card's own (untilted) frame, from its top-left corner.
                val u = c * (x - cx) + s * (y - cy) + cardW / 2
                val v = -s * (x - cx) + c * (y - cy) + cardH / 2
                val base = when {
                    u in 0f..cardW && v in 0f..cardH -> if (text && isText(u, v)) 40 else card
                    else -> extra(x, y) ?: background
                }
                (base + random.nextInt(-noise, noise + 1)).coerceIn(0, 255)
            }
            return GrayImage(width, height, pixels)
        }

        // Name, title and three contact lines, like a real card.
        private fun isText(u: Float, v: Float): Boolean {
            val lines = listOf(0.18f to 0.55f, 0.32f to 0.4f, 0.55f to 0.6f, 0.67f to 0.5f, 0.79f to 0.65f)
            return lines.any { (row, length) -> v in cardH * row..cardH * row + 6f && u in cardW * 0.1f..cardW * (0.1f + length) }
        }

        fun truth() = run {
            // Bounding box of the (possibly tilted) card.
            val rad = Math.toRadians(degrees.toDouble())
            val hw = (abs(cos(rad)) * cardW / 2 + abs(sin(rad)) * cardH / 2).toFloat()
            val hh = (abs(sin(rad)) * cardW / 2 + abs(cos(rad)) * cardH / 2).toFloat()
            NormalizedRect((cx - hw) / width, (cy - hh) / height, (cx + hw) / width, (cy + hh) / height)
        }
    }

    private fun assertFinds(scene: Scene, tolerance: Float = 0.03f) {
        val found = CardEdgeDetector.detect(scene.render())?.bounds
        assertNotNull("no card found", found)
        val t = scene.truth()
        val f = found!!
        // The crop must keep the whole card (a hair of slack) and add at most a thin border around it.
        val outward = listOf(t.left - f.left, t.top - f.top, f.right - t.right, f.bottom - t.bottom)
        assertTrue("found $f, expected about $t", outward.all { it >= -0.005f && it <= tolerance })
    }

    @Test fun lightCard_onDarkTable() = assertFinds(Scene())

    @Test fun darkCard_onLightTable() = assertFinds(Scene(background = 210, card = 70, text = false))

    @Test fun lowContrast_whiteCardOnGreyDesk() = assertFinds(Scene(background = 190, card = 228, noise = 5))

    @Test fun unevenLight_acrossTheTable() = assertFinds(Scene(extra = { x, _ -> 30 + x * 100 / 320 }))

    @Test fun tiltedCard() = assertFinds(Scene(degrees = 5f), tolerance = 0.04f)

    @Test fun cardOffCentre_andSmaller() = assertFinds(Scene(cx = 120f, cy = 140f, cardW = 150f, cardH = 90f))

    @Test fun portraitPhoto_ofAnUprightCard() = assertFinds(Scene(width = 240, height = 320, cx = 120f, cy = 160f, cardW = 120f, cardH = 200f, text = false))

    @Test fun ignoresALongTableEdgeBehindTheCard() = assertFinds(
        Scene(extra = { _, y -> if (y > 215) 150 else null }),
    )

    @Test fun tiltedCard_cornersAreFoundForStraightening() {
        val scene = Scene(degrees = 6f, text = false)
        val quad = CardEdgeDetector.detect(scene.render())
        assertNotNull(quad)
        val rad = Math.toRadians(6.0)
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // Card corners, from its own frame back to the photo (clockwise from top-left).
        val expected = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (u, v) ->
            val du = u * scene.cardW / 2
            val dv = v * scene.cardH / 2
            NormalizedPoint((scene.cx + c * du - s * dv) / scene.width, (scene.cy + s * du + c * dv) / scene.height)
        }
        quad!!.corners.zip(expected).forEach { (got, want) ->
            assertTrue("corner $got, expected about $want", abs(got.x - want.x) < 0.025f && abs(got.y - want.y) < 0.025f)
        }
        assertTrue(CropMath.isValid(quad))
    }

    @Test fun noCard_returnsNull() {
        assertNull(CardEdgeDetector.detect(Scene(cardW = 0f, cardH = 0f).render()))
    }

    @Test fun textAlone_isNotACard() {
        // A page of text lines with no card outline.
        val scene = Scene(background = 230, card = 230, noise = 4)
        assertNull(CardEdgeDetector.detect(scene.render()))
    }

    @Test fun cardFillingThePhoto_returnsNull() {
        // Edges outside the photo: nothing to find, the default crop stays.
        assertNull(CardEdgeDetector.detect(Scene(cardW = 400f, cardH = 300f).render()))
    }

    @Test fun tinyImage_returnsNull() {
        assertNull(CardEdgeDetector.detect(GrayImage(10, 10, IntArray(100))))
    }
}
