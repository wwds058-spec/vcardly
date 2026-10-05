package com.yasin.vcardly.domain.scan

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

enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

object CropMath {
    const val MIN_SIZE = 0.1f

    /** Applies a drag of ([dx],[dy]) (fractions of the image size) to [handle], keeping the rect valid. */
    fun drag(rect: NormalizedRect, handle: CropHandle, dx: Float, dy: Float): NormalizedRect = when (handle) {
        CropHandle.MOVE -> {
            val mx = dx.coerceIn(-rect.left, 1f - rect.right)
            val my = dy.coerceIn(-rect.top, 1f - rect.bottom)
            NormalizedRect(rect.left + mx, rect.top + my, rect.right + mx, rect.bottom + my)
        }
        CropHandle.TOP_LEFT -> rect.copy(
            left = (rect.left + dx).coerceIn(0f, rect.right - MIN_SIZE),
            top = (rect.top + dy).coerceIn(0f, rect.bottom - MIN_SIZE),
        )
        CropHandle.TOP_RIGHT -> rect.copy(
            right = (rect.right + dx).coerceIn(rect.left + MIN_SIZE, 1f),
            top = (rect.top + dy).coerceIn(0f, rect.bottom - MIN_SIZE),
        )
        CropHandle.BOTTOM_LEFT -> rect.copy(
            left = (rect.left + dx).coerceIn(0f, rect.right - MIN_SIZE),
            bottom = (rect.bottom + dy).coerceIn(rect.top + MIN_SIZE, 1f),
        )
        CropHandle.BOTTOM_RIGHT -> rect.copy(
            right = (rect.right + dx).coerceIn(rect.left + MIN_SIZE, 1f),
            bottom = (rect.bottom + dy).coerceIn(rect.top + MIN_SIZE, 1f),
        )
    }

    /** Pixel crop box for an image of [width] x [height], clamped inside the image and at least 1px. */
    fun toPixels(rect: NormalizedRect, width: Int, height: Int): IntArray {
        val l = (rect.left * width).toInt().coerceIn(0, width - 1)
        val t = (rect.top * height).toInt().coerceIn(0, height - 1)
        val r = (rect.right * width).toInt().coerceIn(l + 1, width)
        val b = (rect.bottom * height).toInt().coerceIn(t + 1, height)
        return intArrayOf(l, t, r - l, b - t) // x, y, w, h
    }

    /**
     * Which handle a touch at ([x],[y]) (pixels, in a [width] x [height] image view) grabs: the nearest corner
     * within [radiusPx], else MOVE when inside the rect, else null.
     */
    fun hitTest(rect: NormalizedRect, x: Float, y: Float, width: Float, height: Float, radiusPx: Float): CropHandle? {
        val corners = listOf(
            CropHandle.TOP_LEFT to (rect.left to rect.top),
            CropHandle.TOP_RIGHT to (rect.right to rect.top),
            CropHandle.BOTTOM_LEFT to (rect.left to rect.bottom),
            CropHandle.BOTTOM_RIGHT to (rect.right to rect.bottom),
        )
        val nearest = corners
            .map { (handle, p) -> handle to Math.hypot((p.first * width - x).toDouble(), (p.second * height - y).toDouble()) }
            .minByOrNull { it.second }
        if (nearest != null && nearest.second <= radiusPx) return nearest.first
        val inside = x / width in rect.left..rect.right && y / height in rect.top..rect.bottom
        return if (inside) CropHandle.MOVE else null
    }
}
