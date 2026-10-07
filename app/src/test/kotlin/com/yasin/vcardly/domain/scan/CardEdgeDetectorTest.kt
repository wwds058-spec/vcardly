package com.yasin.vcardly.domain.scan

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
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

    // ---- Real-world shapes: perspective, larger tilt, rounded corners, colour-only edges ----

    /** Photo channels (r, g, b) of a card whose corners are [corners] (pixels, clockwise from top-left). */
    private fun quadPhoto(
        corners: List<Pair<Float, Float>>,
        card: Triple<Int, Int, Int> = Triple(230, 228, 222),
        table: Triple<Int, Int, Int> = Triple(70, 60, 55),
        width: Int = 320,
        height: Int = 240,
        cornerRadius: Float = 0f,
        shadow: Boolean = false,
        noise: Int = 6,
    ): List<GrayImage> {
        val random = Random(7)
        val rgb = Array(3) { IntArray(width * height) }
        fun inside(x: Float, y: Float) = corners.indices.all { i ->
            val (ax, ay) = corners[i]
            val (bx, by) = corners[(i + 1) % 4]
            (bx - ax) * (y - ay) - (by - ay) * (x - ax) >= 0f
        }
        val (minX, maxX) = corners.minOf { it.first } to corners.maxOf { it.first }
        val (minY, maxY) = corners.minOf { it.second } to corners.maxOf { it.second }
        for (y in 0 until height) for (x in 0 until width) {
            val fx = x.toFloat()
            val fy = y.toFloat()
            var isCard = inside(fx, fy)
            if (isCard && cornerRadius > 0f) {
                // Rounded corners (for an upright card): cut the outside of a circle in each corner square.
                val cx = if (fx < minX + cornerRadius) minX + cornerRadius else if (fx > maxX - cornerRadius) maxX - cornerRadius else fx
                val cy = if (fy < minY + cornerRadius) minY + cornerRadius else if (fy > maxY - cornerRadius) maxY - cornerRadius else fy
                if (hypot(fx - cx, fy - cy) > cornerRadius) isCard = false
            }
            val inShadow = shadow && !isCard && inside(fx - 6f, fy - 6f)
            val base = when {
                isCard -> card
                inShadow -> Triple(table.first * 6 / 10, table.second * 6 / 10, table.third * 6 / 10)
                else -> table
            }
            val n = random.nextInt(-noise, noise + 1)
            val i = y * width + x
            rgb[0][i] = (base.first + n).coerceIn(0, 255)
            rgb[1][i] = (base.second + n).coerceIn(0, 255)
            rgb[2][i] = (base.third + n).coerceIn(0, 255)
        }
        return rgb.map { GrayImage(width, height, it) }
    }

    private fun assertCorners(found: CropQuad?, corners: List<Pair<Float, Float>>, width: Int = 320, height: Int = 240, tolerance: Float = 0.03f) {
        assertNotNull("no card found", found)
        found!!.corners.zip(corners).forEach { (got, want) ->
            val wx = want.first / width
            val wy = want.second / height
            assertTrue("corner $got, expected about ($wx, $wy)", abs(got.x - wx) < tolerance && abs(got.y - wy) < tolerance)
        }
    }

    @Test fun perspective_cardPhotographedAtAnAngle() {
        // The far (top) edge looks shorter than the near one: a trapezoid, sides not parallel.
        val corners = listOf(90f to 55f, 230f to 55f, 265f to 190f, 55f to 190f)
        assertCorners(CardEdgeDetector.detect(quadPhoto(corners)), corners)
    }

    @Test fun perspective_sideways() {
        val corners = listOf(60f to 70f, 255f to 40f, 255f to 205f, 60f to 175f)
        assertCorners(CardEdgeDetector.detect(quadPhoto(corners)), corners)
    }

    @Test fun tiltedFifteenDegrees() {
        val rad = Math.toRadians(15.0)
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (u, v) ->
            val du = u * 100f
            val dv = v * 60f
            160f + c * du - s * dv to 120f + s * du + c * dv
        }
        assertCorners(CardEdgeDetector.detect(quadPhoto(corners)), corners)
    }

    @Test fun roundedCorners_withAShadow() {
        val corners = listOf(55f to 50f, 265f to 50f, 265f to 180f, 55f to 180f)
        val found = CardEdgeDetector.detect(quadPhoto(corners, cornerRadius = 14f, shadow = true))
        assertCorners(found, corners, tolerance = 0.035f)
    }

    @Test fun colourOnlyEdge_beigeCardOnBlueDesk() {
        // Almost equally bright (luminance about 182 and 176): only the colour changes at the card's edge.
        val corners = listOf(60f to 55f, 260f to 55f, 260f to 185f, 60f to 185f)
        val photo = quadPhoto(corners, card = Triple(200, 185, 120), table = Triple(60, 220, 255), noise = 4)
        assertCorners(CardEdgeDetector.detect(photo), corners)
        // Brightness alone does not show it.
        val luminance = GrayImage(320, 240, IntArray(320 * 240) { i -> (photo[0].pixels[i] * 299 + photo[1].pixels[i] * 587 + photo[2].pixels[i] * 114) / 1000 })
        assertNull(CardEdgeDetector.detect(luminance))
    }

    @Test fun woodGrainTable() {
        // Streaky texture behind the card, the kind of desk cards are photographed on.
        val corners = listOf(65f to 50f, 255f to 62f, 250f to 185f, 60f to 178f)
        val wood = quadPhoto(corners, table = Triple(150, 100, 60), noise = 3).map { channel ->
            GrayImage(320, 240, IntArray(320 * 240) { i ->
                val x = i % 320
                val y = i / 320
                val inCard = channel.pixels[i] > 200
                if (inCard) channel.pixels[i] else (channel.pixels[i] + (18 * sin(y * 0.9 + sin(x * 0.05) * 3)).toInt()).coerceIn(0, 255)
            })
        }
        assertCorners(CardEdgeDetector.detect(wood), corners, tolerance = 0.035f)
    }

    private fun tiles(x: Int, y: Int) = if ((x / 23 + y / 17) % 2 == 0) 170 else 90

    @Test fun tiledFloor_withoutACard_isNotACard() {
        val r = Random(1)
        val floor = GrayImage(320, 240, IntArray(320 * 240) { i -> tiles(i % 320, i / 320) + r.nextInt(-6, 7) })
        assertNull(CardEdgeDetector.detect(floor))
    }

    @Test fun cardOnATiledFloor() {
        val corners = listOf(70f to 60f, 250f to 60f, 250f to 175f, 70f to 175f)
        val photo = quadPhoto(corners, card = Triple(245, 245, 245), noise = 4).map { channel ->
            GrayImage(320, 240, IntArray(320 * 240) { i -> if (channel.pixels[i] > 230) channel.pixels[i] else tiles(i % 320, i / 320) })
        }
        assertCorners(CardEdgeDetector.detect(photo), corners)
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
