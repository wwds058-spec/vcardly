package com.yasin.vcardly.domain.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {
    private val q = CropQuad.of(NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f))

    @Test fun move_isClampedToImage() {
        val m = CropMath.drag(q, CropHandle.MOVE, 0.5f, -0.5f)
        assertEquals(0.4f, m.topLeft.x, 1e-5f); assertEquals(1f, m.topRight.x, 1e-5f)
        assertEquals(0f, m.topLeft.y, 1e-5f); assertEquals(0.6f, m.bottomLeft.y, 1e-5f)
        assertTrue(m.isUpright)
    }

    @Test fun corner_movesFreely_soATiltedCardCanBeTraced() {
        val m = CropMath.drag(q, CropHandle.TOP_RIGHT, 0.1f, 0.05f)
        assertEquals(0.9f, m.topRight.x, 1e-5f); assertEquals(0.25f, m.topRight.y, 1e-5f)
        assertEquals(q.topLeft, m.topLeft)
        assertFalse(m.isUpright)
        assertTrue(CropMath.isValid(m))
    }

    @Test fun corner_staysInsideTheImage() {
        val m = CropMath.drag(q, CropHandle.BOTTOM_LEFT, -2f, 2f)
        assertEquals(NormalizedPoint(0f, 1f), m.bottomLeft)
    }

    @Test fun corner_cannotFoldTheSelection() {
        // Dragging the top-left corner past the bottom-right one would turn the shape inside out: refused.
        assertEquals(q, CropMath.drag(q, CropHandle.TOP_LEFT, 0.7f, 0.7f))
        // Nor may two corners meet (side shorter than MIN_SIZE).
        assertEquals(q, CropMath.drag(q, CropHandle.TOP_LEFT, 0.55f, 0f))
    }

    @Test fun validity() {
        assertTrue(CropMath.isValid(CropQuad.Full))
        assertTrue(CropMath.isValid(CropQuad.Default))
        val crossed = q.copy(topRight = q.bottomRight, bottomRight = q.topRight)
        assertFalse(CropMath.isValid(crossed))
        val concave = q.copy(topRight = NormalizedPoint(0.3f, 0.3f))
        assertFalse(CropMath.isValid(concave))
    }

    @Test fun toPixels_clampsAndNeverEmpty() {
        val px = CropMath.toPixels(NormalizedRect.Full, 1000, 600)
        assertArrayEquals(intArrayOf(0, 0, 1000, 600), px)
        val tiny = CropMath.toPixels(NormalizedRect(0.9999f, 0.9999f, 1f, 1f), 100, 100)
        assertTrue(tiny[2] >= 1 && tiny[3] >= 1)
        assertArrayEquals(floatArrayOf(200f, 120f, 800f, 120f, 800f, 480f, 200f, 480f), CropMath.toPixels(q, 1000, 600), 1e-3f)
    }

    @Test fun outputSize_usesTheLongerOfOppositeSides() {
        // A card seen at an angle: the near (bottom) side looks longer than the far (top) side.
        val trapezoid = CropQuad(
            NormalizedPoint(0.3f, 0.2f), NormalizedPoint(0.7f, 0.2f),
            NormalizedPoint(0.9f, 0.6f), NormalizedPoint(0.1f, 0.6f),
        )
        val (w, h) = CropMath.outputSize(trapezoid, 1000, 1000).toList()
        assertEquals(800, w)
        assertEquals(447, h) // hypot(200, 400)
        assertArrayEquals(intArrayOf(600, 360), CropMath.outputSize(q, 1000, 600))
    }

    @Test fun bounds_andUpright() {
        assertEquals(NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f), q.bounds)
        assertTrue(q.isUpright)
        val tilted = q.copy(topLeft = NormalizedPoint(0.1f, 0.25f))
        assertEquals(NormalizedRect(0.1f, 0.2f, 0.8f, 0.8f), tilted.bounds)
        assertFalse(tilted.isUpright)
    }

    @Test fun hitTest_prefersCorners_thenInside() {
        assertEquals(CropHandle.TOP_LEFT, CropMath.hitTest(q, 210f, 110f, 1000f, 500f, 40f))
        assertEquals(CropHandle.BOTTOM_RIGHT, CropMath.hitTest(q, 790f, 390f, 1000f, 500f, 40f))
        assertEquals(CropHandle.MOVE, CropMath.hitTest(q, 500f, 250f, 1000f, 500f, 40f))
        assertEquals(null, CropMath.hitTest(q, 20f, 20f, 1000f, 500f, 40f))
        // Inside the bounding box but outside a slanted side is not a move.
        val slanted = q.copy(topLeft = NormalizedPoint(0.5f, 0.2f))
        assertEquals(null, CropMath.hitTest(slanted, 250f, 150f, 1000f, 500f, 40f))
    }
}
