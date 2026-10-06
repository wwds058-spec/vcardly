package com.yasin.vcardly.domain.scan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A rectangle in 0..1 coordinates relative to the (already rotated) image. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val Full = NormalizedRect(0f, 0f, 1f, 1f)
        /** Initial selection: slightly inset so the handles are visible and grabbable. */
        val Default = NormalizedRect(0.05f, 0.05f, 0.95f, 0.95f)
    }
}

/** A point in 0..1 coordinates relative to the (already rotated) image. */
data class NormalizedPoint(val x: Float, val y: Float)

/**
 * The part of the photo to keep: four corners, clockwise from top-left. They need not form a rectangle; a card photographed
 * at an angle is straightened into one when the crop is applied (see [CropMath.outputSize]).
 */
data class CropQuad(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
) {
    val corners: List<NormalizedPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Smallest rectangle around the four corners. */
    val bounds: NormalizedRect
        get() = NormalizedRect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })

    /** True when the corners already form an upright rectangle, so a plain crop gives the same result as straightening. */
    val isUpright: Boolean
        get() = abs(topLeft.x - bottomLeft.x) < EPS && abs(topRight.x - bottomRight.x) < EPS &&
            abs(topLeft.y - topRight.y) < EPS && abs(bottomLeft.y - bottomRight.y) < EPS

    fun with(handle: CropHandle, point: NormalizedPoint): CropQuad = when (handle) {
        CropHandle.TOP_LEFT -> copy(topLeft = point)
        CropHandle.TOP_RIGHT -> copy(topRight = point)
        CropHandle.BOTTOM_RIGHT -> copy(bottomRight = point)
        CropHandle.BOTTOM_LEFT -> copy(bottomLeft = point)
        CropHandle.MOVE -> this
    }

    fun corner(handle: CropHandle): NormalizedPoint? = when (handle) {
        CropHandle.TOP_LEFT -> topLeft
        CropHandle.TOP_RIGHT -> topRight
        CropHandle.BOTTOM_RIGHT -> bottomRight
        CropHandle.BOTTOM_LEFT -> bottomLeft
        CropHandle.MOVE -> null
    }

    companion object {
        private const val EPS = 1e-4f

        fun of(rect: NormalizedRect) = CropQuad(
            NormalizedPoint(rect.left, rect.top),
            NormalizedPoint(rect.right, rect.top),
            NormalizedPoint(rect.right, rect.bottom),
            NormalizedPoint(rect.left, rect.bottom),
        )

        val Full = of(NormalizedRect.Full)
        val Default = of(NormalizedRect.Default)
    }
}

enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, MOVE }

object CropMath {
    /** Every side of the selection stays at least this long (a fraction of the image), so it can always be grabbed again. */
    const val MIN_SIZE = 0.1f

    /**
     * Applies a drag of ([dx],[dy]) (fractions of the image size) to [handle]. A corner moves freely inside the image, but a
     * move that would fold the selection over itself or make a side shorter than [MIN_SIZE] is refused (the corner stays).
     * MOVE shifts the whole selection, stopping at the image edges.
     */
    fun drag(quad: CropQuad, handle: CropHandle, dx: Float, dy: Float): CropQuad {
        if (handle == CropHandle.MOVE) {
            val mx = dx.coerceIn(-quad.corners.minOf { it.x }, 1f - quad.corners.maxOf { it.x })
            val my = dy.coerceIn(-quad.corners.minOf { it.y }, 1f - quad.corners.maxOf { it.y })
            return CropQuad(
                quad.topLeft.shift(mx, my), quad.topRight.shift(mx, my),
                quad.bottomRight.shift(mx, my), quad.bottomLeft.shift(mx, my),
            )
        }
        val from = quad.corner(handle) ?: return quad
        val to = NormalizedPoint((from.x + dx).coerceIn(0f, 1f), (from.y + dy).coerceIn(0f, 1f))
        val moved = quad.with(handle, to)
        return if (isValid(moved)) moved else quad
    }

    /** Convex, corners in clockwise order, and no side shorter than [MIN_SIZE]. */
    fun isValid(quad: CropQuad): Boolean {
        val c = quad.corners
        for (i in c.indices) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            val d = c[(i + 2) % 4]
            if (hypot(b.x - a.x, b.y - a.y) < MIN_SIZE) return false
            // Turning the same way at every corner (clockwise on screen, where y grows downwards) means convex.
            val cross = (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
            if (cross <= 0f) return false
        }
        return true
    }

    /** Pixel crop box for an image of [width] x [height], clamped inside the image and at least 1px. */
    fun toPixels(rect: NormalizedRect, width: Int, height: Int): IntArray {
        val l = (rect.left * width).toInt().coerceIn(0, width - 1)
        val t = (rect.top * height).toInt().coerceIn(0, height - 1)
        val r = (rect.right * width).toInt().coerceIn(l + 1, width)
        val b = (rect.bottom * height).toInt().coerceIn(t + 1, height)
        return intArrayOf(l, t, r - l, b - t) // x, y, w, h
    }

    /** The four corners in pixels of a [width] x [height] image, as x, y pairs clockwise from top-left. */
    fun toPixels(quad: CropQuad, width: Int, height: Int): FloatArray =
        quad.corners.flatMap { listOf(it.x * width, it.y * height) }.toFloatArray()

    /**
     * Size in pixels of the straightened card: the longer of each pair of opposite sides, so no detail is squeezed. A card
     * photographed at an angle comes out with its real proportions (the near side is the longer one).
     */
    fun outputSize(quad: CropQuad, width: Int, height: Int): IntArray {
        fun side(a: NormalizedPoint, b: NormalizedPoint) = hypot((b.x - a.x) * width, (b.y - a.y) * height)
        val w = max(side(quad.topLeft, quad.topRight), side(quad.bottomLeft, quad.bottomRight))
        val h = max(side(quad.topLeft, quad.bottomLeft), side(quad.topRight, quad.bottomRight))
        return intArrayOf(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
    }

    /**
     * Which handle a touch at ([x],[y]) (pixels, in a [width] x [height] image view) grabs: the nearest corner
     * within [radiusPx], else MOVE when inside the selection, else null.
     */
    fun hitTest(quad: CropQuad, x: Float, y: Float, width: Float, height: Float, radiusPx: Float): CropHandle? {
        val handles = listOf(CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT, CropHandle.BOTTOM_LEFT)
        val nearest = handles
            .map { h -> val p = quad.corner(h)!!; h to hypot(p.x * width - x, p.y * height - y) }
            .minByOrNull { it.second }
        if (nearest != null && nearest.second <= radiusPx) return nearest.first
        return if (contains(quad, x / width, y / height)) CropHandle.MOVE else null
    }

    /** Point-in-convex-polygon: on the inner side of every edge. */
    fun contains(quad: CropQuad, x: Float, y: Float): Boolean {
        val c = quad.corners
        return c.indices.all { i ->
            val a = c[i]
            val b = c[(i + 1) % 4]
            (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) >= 0f
        }
    }

    private fun NormalizedPoint.shift(dx: Float, dy: Float) = NormalizedPoint(x + dx, y + dy)
}
