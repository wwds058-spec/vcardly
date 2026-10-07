package com.yasin.vcardly.domain.scan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One channel of a picture (luminance, or red, green or blue): [pixels] holds 0..255 per pixel, row by row. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height)
    }
}

/**
 * Finds a visiting card in a photo, so the crop starts on the card instead of the whole picture. Runs on the device, on a
 * small copy of the photo (about 320 px), in plain Kotlin.
 *
 * A card shows up as four long, straight edges that close a four-sided shape. The detector marks strong edges (in
 * brightness or in colour, so a beige card on a blue desk counts even when both are equally bright), collects straight lines
 * up to about 20 degrees from horizontal or vertical that many edge pixels lie on, and tries every pair of top/bottom lines
 * with every pair of left/right lines. Opposite sides need not be parallel, so a card photographed at an angle (a
 * trapezoid in the photo) is found and can be straightened. A combination counts only when all four sides are traced along
 * most of their length (rounded corners are ignored), the shape is convex, roughly card-shaped and covers a fair part of the
 * photo. Lines of text on the card are long too, but they never close such a shape. If nothing qualifies with strict edges,
 * a looser edge threshold is tried for low-contrast photos; still nothing means null, and the caller keeps its default crop.
 */
object CardEdgeDetector {
    private const val MAX_SLOPE = 0.42f // about 23 degrees
    private const val SLOPE_STEP = 0.03f
    private const val MIN_EDGE = 24 // Sobel magnitude (|gx| + |gy|) on the blurred image
    // Times the photo's median gradient (its texture and noise; a card full of text cannot move the median the way it moves
    // a high percentile). Strict first, then looser for low-contrast photos.
    private val THRESHOLD_FACTORS = floatArrayOf(4f, 2.5f)
    private const val MIN_LINE = 0.15f // a candidate line has edge pixels along at least 15% of the picture
    private const val CANDIDATES = 14
    private const val MIN_COVERAGE = 0.55f // every side is traced along at least 55% of its middle 80%
    private const val SIDE_MARGIN = 0.1f // ignore 10% at each end of a side: rounded corners, shadows
    private const val MAX_SKEW = 0.6f // slope difference between opposite sides (perspective)
    private const val MIN_AREA = 0.1f
    private const val MIN_SIDE = 0.2f
    private const val MIN_RATIO = 1.05f // long side / short side as photographed; perspective changes the printed 1.5 to 1.8
    private const val MAX_RATIO = 2.8f
    private const val GROW = 1.02f // corners pushed 2% outwards from the centre, so the crop never shaves the card's edge

    /** A straight line: across = [offset] + [slope] * (along - centre of the picture). */
    private class Line(val offset: Float, val slope: Float, val count: Int)

    /** Edge pixels by orientation, and which way brightness changes across each (+1 or -1, 0 when not an edge). */
    private class Edges(val width: Int, val height: Int, val horizontal: BooleanArray, val vertical: BooleanArray, val sign: ByteArray)

    private class Gradients(val width: Int, val height: Int, val gx: IntArray, val gy: IntArray, val median: Int)

    /** The card's four corners, clockwise from top-left, or null when no card outline is clear enough. */
    fun detect(image: GrayImage): CropQuad? = detect(listOf(image))

    /** As [detect], looking at several channels of the same photo (for example red, green and blue). */
    fun detect(channels: List<GrayImage>): CropQuad? {
        val first = channels.firstOrNull() ?: return null
        require(channels.all { it.width == first.width && it.height == first.height })
        if (first.width < 32 || first.height < 32) return null
        val gradients = gradients(channels)
        for (factor in THRESHOLD_FACTORS) {
            val threshold = max(MIN_EDGE, (gradients.median * factor).roundToInt())
            findQuad(edges(gradients, threshold))?.let { return it }
        }
        return null
    }

    private fun findQuad(edges: Edges): CropQuad? {
        val rows = lines(edges, horizontal = true)
        val cols = lines(edges, horizontal = false)
        if (rows.size < 2 || cols.size < 2) return null

        val w = edges.width.toFloat()
        val h = edges.height.toFloat()
        // Each side's coverage depends on its own line and the two lines that cut it; cache it across combinations.
        val n = CANDIDATES
        val rowCover = FloatArray(n * n * n) { Float.NaN }
        val colCover = FloatArray(n * n * n) { Float.NaN }
        var best: List<Pair<Float, Float>>? = null
        var bestScore = 0f
        for ((ti, top) in rows.withIndex()) for ((bi, bottom) in rows.withIndex()) {
            if (bottom.offset - top.offset < MIN_SIDE * h || abs(top.slope - bottom.slope) > MAX_SKEW) continue
            for ((li, left) in cols.withIndex()) for ((ri, right) in cols.withIndex()) {
                if (right.offset - left.offset < MIN_SIDE * w || abs(left.slope - right.slope) > MAX_SKEW) continue
                val tl = corner(top, left, w, h)
                val tr = corner(top, right, w, h)
                val bl = corner(bottom, left, w, h)
                val br = corner(bottom, right, w, h)
                val quad = listOf(tl, tr, br, bl)
                if (quad.any { (x, y) -> x < 0f || y < 0f || x > w - 1 || y > h - 1 }) continue
                if (!convex(quad)) continue
                val across = (dist(tl, tr) + dist(bl, br)) / 2
                val down = (dist(tl, bl) + dist(tr, br)) / 2
                val ratio = max(across, down) / min(across, down)
                if (ratio < MIN_RATIO || ratio > MAX_RATIO) continue
                val area = area(quad) / (w * h)
                if (area < MIN_AREA) continue
                fun cached(cache: FloatArray, line: Int, a: Int, b: Int, compute: () -> Float): Float {
                    val key = (line * n + a) * n + b
                    if (cache[key].isNaN()) cache[key] = compute()
                    return cache[key]
                }
                val sTop = cached(rowCover, ti, li, ri) { coverage(edges, tl, tr, horizontal = true) }
                if (sTop < MIN_COVERAGE) continue
                val sBottom = cached(rowCover, bi, li, ri) { coverage(edges, bl, br, horizontal = true) }
                if (sBottom < MIN_COVERAGE) continue
                val sLeft = cached(colCover, li, ti, bi) { coverage(edges, tl, bl, horizontal = false) }
                if (sLeft < MIN_COVERAGE) continue
                val sRight = cached(colCover, ri, ti, bi) { coverage(edges, tr, br, horizontal = false) }
                if (sRight < MIN_COVERAGE) continue
                val score = (sTop + sBottom + sLeft + sRight) / 4 + 0.15f * area
                if (score > bestScore) {
                    bestScore = score
                    best = quad
                }
            }
        }
        val quad = best ?: return null
        val cx = quad.sumOf { it.first.toDouble() }.toFloat() / 4
        val cy = quad.sumOf { it.second.toDouble() }.toFloat() / 4
        val (tl, tr, br, bl) = quad.map { (x, y) ->
            NormalizedPoint(((cx + (x - cx) * GROW) / w).coerceIn(0f, 1f), ((cy + (y - cy) * GROW) / h).coerceIn(0f, 1f))
        }
        return CropQuad(tl, tr, br, bl)
    }

    /**
     * Light 3x3 blur against sensor noise, then Sobel on every channel; each pixel keeps the gradient of the channel where
     * the edge is strongest. Also returns the median magnitude, a measure of the photo's own texture and noise.
     */
    private fun gradients(channels: List<GrayImage>): Gradients {
        val w = channels[0].width
        val h = channels[0].height
        val gx = IntArray(w * h)
        val gy = IntArray(w * h)
        val best = IntArray(w * h)
        val blurred = IntArray(w * h)
        for (channel in channels) {
            val src = channel.pixels
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0
                var count = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val yy = y + dy
                    val xx = x + dx
                    if (yy in 0 until h && xx in 0 until w) {
                        sum += src[yy * w + xx]
                        count++
                    }
                }
                blurred[y * w + x] = sum / count
            }
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                fun p(dx: Int, dy: Int) = blurred[(y + dy) * w + x + dx]
                val sx = p(1, -1) + 2 * p(1, 0) + p(1, 1) - p(-1, -1) - 2 * p(-1, 0) - p(-1, 1)
                val sy = p(-1, 1) + 2 * p(0, 1) + p(1, 1) - p(-1, -1) - 2 * p(0, -1) - p(1, -1)
                val i = y * w + x
                val magnitude = abs(sx) + abs(sy)
                if (magnitude > best[i]) {
                    best[i] = magnitude
                    gx[i] = sx
                    gy[i] = sy
                }
            }
        }
        val histogram = IntArray(2048)
        for (y in 1 until h - 1) for (x in 1 until w - 1) histogram[min(best[y * w + x], histogram.lastIndex)]++
        val target = (w - 2) * (h - 2) / 2
        var seen = 0
        var median = 0
        while (median < histogram.lastIndex && seen + histogram[median] < target) seen += histogram[median++]
        return Gradients(w, h, gx, gy, median)
    }

    /** Pixels whose gradient reaches [threshold], filed as part of a horizontal or a vertical edge. */
    private fun edges(g: Gradients, threshold: Int): Edges {
        val horizontal = BooleanArray(g.width * g.height)
        val vertical = BooleanArray(g.width * g.height)
        val sign = ByteArray(g.width * g.height)
        for (i in horizontal.indices) {
            val ax = abs(g.gx[i])
            val ay = abs(g.gy[i])
            if (ax + ay < threshold) continue
            if (ay >= ax) {
                horizontal[i] = true
                sign[i] = if (g.gy[i] >= 0) 1 else -1
            } else {
                vertical[i] = true
                sign[i] = if (g.gx[i] >= 0) 1 else -1
            }
        }
        return Edges(g.width, g.height, horizontal, vertical, sign)
    }

    private fun isEdge(edges: Edges, along: Int, across: Int, horizontal: Boolean): Boolean {
        val x = if (horizontal) along else across
        val y = if (horizontal) across else along
        if (x !in 0 until edges.width || y !in 0 until edges.height) return false
        return (if (horizontal) edges.horizontal else edges.vertical)[y * edges.width + x]
    }

    /** The strongest straight lines of one orientation, nearly-duplicate lines removed. */
    private fun lines(edges: Edges, horizontal: Boolean): List<Line> {
        val alongSize = if (horizontal) edges.width else edges.height
        val acrossSize = if (horizontal) edges.height else edges.width
        val centre = alongSize / 2f
        val steps = (MAX_SLOPE / SLOPE_STEP).roundToInt()
        val all = ArrayList<Line>()
        for (s in -steps..steps) {
            val slope = s * SLOPE_STEP
            for (offset in 0 until acrossSize) {
                var count = 0
                for (along in 0 until alongSize) {
                    val across = (offset + slope * (along - centre)).roundToInt()
                    if (isEdge(edges, along, across, horizontal)) count++
                }
                if (count >= MIN_LINE * alongSize) all += Line(offset.toFloat(), slope, count)
            }
        }
        all.sortByDescending { it.count }
        val kept = ArrayList<Line>()
        for (line in all) {
            if (kept.none { abs(it.offset - line.offset) < 6f }) kept += line
            if (kept.size == CANDIDATES) break
        }
        return kept.sortedBy { it.offset }
    }

    /** Where horizontal line [row] meets vertical line [col], in pixels. */
    private fun corner(row: Line, col: Line, w: Float, h: Float): Pair<Float, Float> {
        val cx = w / 2
        val cy = h / 2
        // y = row.offset + row.slope * (x - cx) and x = col.offset + col.slope * (y - cy)
        val x = (col.offset + col.slope * (row.offset - row.slope * cx - cy)) / (1 - col.slope * row.slope)
        val y = row.offset + row.slope * (x - cx)
        return x to y
    }

    /**
     * Share of the middle of the side from [a] to [b] (its ends are skipped, where rounded corners curve away) that has an
     * edge pixel of the right orientation within two pixels of it, all changing brightness the same way. A card's edge keeps
     * one direction along its whole length (card lighter than the desk, or darker); a line through a tiled or checked
     * pattern flips at every square, so only about half of it counts.
     */
    private fun coverage(edges: Edges, a: Pair<Float, Float>, b: Pair<Float, Float>, horizontal: Boolean): Float {
        val n = dist(a, b).roundToInt().coerceAtLeast(1)
        val from = (n * SIDE_MARGIN).roundToInt()
        val to = n - from
        var rising = 0
        var falling = 0
        for (i in from..to) {
            val t = i.toFloat() / n
            val x = (a.first + (b.first - a.first) * t).roundToInt()
            val y = (a.second + (b.second - a.second) * t).roundToInt()
            val along = if (horizontal) x else y
            val across = if (horizontal) y else x
            val hit = (-2..2).firstOrNull { isEdge(edges, along, across + it, horizontal) } ?: continue
            val px = if (horizontal) along else across + hit
            val py = if (horizontal) across + hit else along
            if (edges.sign[py * edges.width + px] > 0) rising++ else falling++
        }
        return max(rising, falling).toFloat() / (to - from + 1)
    }

    /** Clockwise (on screen) at every corner, so the shape does not fold over itself. */
    private fun convex(quad: List<Pair<Float, Float>>): Boolean = quad.indices.all { i ->
        val (ax, ay) = quad[i]
        val (bx, by) = quad[(i + 1) % 4]
        val (cx, cy) = quad[(i + 2) % 4]
        (bx - ax) * (cy - by) - (by - ay) * (cx - bx) > 0f
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>) = hypot(a.first - b.first, a.second - b.second)

    private fun area(quad: List<Pair<Float, Float>>): Float {
        var sum = 0f
        for (i in quad.indices) {
            val (x1, y1) = quad[i]
            val (x2, y2) = quad[(i + 1) % quad.size]
            sum += x1 * y2 - x2 * y1
        }
        return abs(sum) / 2
    }
}
