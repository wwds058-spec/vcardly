package com.yasin.vcardly.domain.scan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A grayscale picture: [pixels] holds the luminance (0..255) of each pixel, row by row. */
class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height)
    }
}

/**
 * Finds a visiting card in a photo, so the crop starts on the card instead of the whole picture. Runs on the device, on a
 * small copy of the photo (about 320 px), in plain Kotlin.
 *
 * A card shows up as four long, straight edges that meet as a rectangle. The detector marks strong brightness edges,
 * collects straight lines (up to about 7 degrees of tilt) that many edge pixels lie on, and tries every pair of
 * top/bottom lines with every pair of left/right lines. A combination counts only when the four sides are actually traced
 * along most of their length, the shape has a card's proportions and covers a fair part of the photo. Lines of text on the
 * card are long too, but they never trace a closed rectangle, so they lose. When nothing qualifies the result is null and
 * the caller keeps its default crop; the user can always adjust it.
 */
object CardEdgeDetector {
    private const val MAX_SLOPE = 0.12f // about 7 degrees
    private const val SLOPE_STEP = 0.02f
    private const val MIN_EDGE = 40 // Sobel magnitude (|gx| + |gy|) on the blurred image
    private const val MIN_LINE = 0.2f // a candidate line has edge pixels along at least 20% of the picture
    private const val CANDIDATES = 10
    private const val MIN_COVERAGE = 0.6f // every side of the card is traced along at least 60% of its length
    private const val MIN_AREA = 0.12f
    private const val MIN_SIDE = 0.2f
    private const val MIN_RATIO = 1.15f // long side / short side; printed cards are about 1.5 to 1.8
    private const val MAX_RATIO = 2.4f
    private const val MARGIN = 0.01f

    /** A straight line: across = [offset] + [slope] * (along - centre of the picture). */
    private class Line(val offset: Float, val slope: Float, val count: Int)

    private class Edges(val width: Int, val height: Int, val horizontal: BooleanArray, val vertical: BooleanArray)

    fun detect(image: GrayImage): NormalizedRect? {
        if (image.width < 32 || image.height < 32) return null
        val edges = edges(image)
        val rows = lines(edges, horizontal = true)
        val cols = lines(edges, horizontal = false)
        if (rows.size < 2 || cols.size < 2) return null

        val w = image.width.toFloat()
        val h = image.height.toFloat()
        var best: List<Pair<Float, Float>>? = null
        var bestScore = 0f
        for (top in rows) for (bottom in rows) {
            if (bottom.offset - top.offset < MIN_SIDE * h || abs(top.slope - bottom.slope) > 0.06f) continue
            for (left in cols) for (right in cols) {
                if (right.offset - left.offset < MIN_SIDE * w || abs(left.slope - right.slope) > 0.06f) continue
                // A tilted rectangle's vertical sides lean the opposite way to its horizontal ones.
                if (abs((top.slope + bottom.slope) / 2 + (left.slope + right.slope) / 2) > 0.08f) continue
                val tl = corner(top, left, w, h)
                val tr = corner(top, right, w, h)
                val bl = corner(bottom, left, w, h)
                val br = corner(bottom, right, w, h)
                val quad = listOf(tl, tr, br, bl)
                if (quad.any { (x, y) -> x < 0f || y < 0f || x > w - 1 || y > h - 1 }) continue
                val across = (dist(tl, tr) + dist(bl, br)) / 2
                val down = (dist(tl, bl) + dist(tr, br)) / 2
                val ratio = max(across, down) / min(across, down)
                if (ratio < MIN_RATIO || ratio > MAX_RATIO) continue
                val area = area(quad) / (w * h)
                if (area < MIN_AREA) continue
                val sides = floatArrayOf(
                    coverage(edges, tl, tr, horizontal = true),
                    coverage(edges, bl, br, horizontal = true),
                    coverage(edges, tl, bl, horizontal = false),
                    coverage(edges, tr, br, horizontal = false),
                )
                if (sides.any { it < MIN_COVERAGE }) continue
                val score = sides.average().toFloat() + 0.15f * area
                if (score > bestScore) {
                    bestScore = score
                    best = quad
                }
            }
        }
        val quad = best ?: return null
        return NormalizedRect(
            left = (quad.minOf { it.first } / w - MARGIN).coerceIn(0f, 1f),
            top = (quad.minOf { it.second } / h - MARGIN).coerceIn(0f, 1f),
            right = (quad.maxOf { it.first } / w + MARGIN).coerceIn(0f, 1f),
            bottom = (quad.maxOf { it.second } / h + MARGIN).coerceIn(0f, 1f),
        )
    }

    /** Light 3x3 blur against sensor noise, then Sobel; each strong edge pixel is filed as horizontal or vertical. */
    private fun edges(image: GrayImage): Edges {
        val w = image.width
        val h = image.height
        val src = image.pixels
        val blurred = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val yy = y + dy
                val xx = x + dx
                if (yy in 0 until h && xx in 0 until w) {
                    sum += src[yy * w + xx]
                    n++
                }
            }
            blurred[y * w + x] = sum / n
        }
        val gx = IntArray(w * h)
        val gy = IntArray(w * h)
        val histogram = IntArray(2048)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            fun p(dx: Int, dy: Int) = blurred[(y + dy) * w + x + dx]
            val sx = p(1, -1) + 2 * p(1, 0) + p(1, 1) - p(-1, -1) - 2 * p(-1, 0) - p(-1, 1)
            val sy = p(-1, 1) + 2 * p(0, 1) + p(1, 1) - p(-1, -1) - 2 * p(0, -1) - p(1, -1)
            gx[y * w + x] = sx
            gy[y * w + x] = sy
            histogram[min(abs(sx) + abs(sy), histogram.lastIndex)]++
        }
        // Edges must stand out from the photo's own texture: twice its 90th percentile, and never below MIN_EDGE.
        val target = (w - 2) * (h - 2) * 9 / 10
        var seen = 0
        var p90 = 0
        while (p90 < histogram.lastIndex && seen + histogram[p90] < target) seen += histogram[p90++]
        val threshold = max(MIN_EDGE, p90 * 2)
        val horizontal = BooleanArray(w * h)
        val vertical = BooleanArray(w * h)
        for (i in 0 until w * h) {
            val ax = abs(gx[i])
            val ay = abs(gy[i])
            if (ax + ay < threshold) continue
            if (ay >= ax) horizontal[i] = true else vertical[i] = true
        }
        return Edges(w, h, horizontal, vertical)
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

    /** Share of the side from [a] to [b] that has an edge pixel of the right orientation within one pixel of it. */
    private fun coverage(edges: Edges, a: Pair<Float, Float>, b: Pair<Float, Float>, horizontal: Boolean): Float {
        val n = dist(a, b).roundToInt().coerceAtLeast(1)
        var hits = 0
        for (i in 0..n) {
            val t = i.toFloat() / n
            val x = (a.first + (b.first - a.first) * t).roundToInt()
            val y = (a.second + (b.second - a.second) * t).roundToInt()
            val along = if (horizontal) x else y
            val across = if (horizontal) y else x
            if ((-1..1).any { isEdge(edges, along, across + it, horizontal) }) hits++
        }
        return hits.toFloat() / (n + 1)
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
