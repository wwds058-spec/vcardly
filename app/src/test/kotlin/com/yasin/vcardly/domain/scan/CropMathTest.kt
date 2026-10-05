package com.yasin.vcardly.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {
    private val r = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)

    @Test fun move_isClampedToImage() {
        val m = CropMath.drag(r, CropHandle.MOVE, 0.5f, -0.5f)
        assertEquals(0.4f, m.left, 1e-5f); assertEquals(1f, m.right, 1e-5f)
        assertEquals(0f, m.top, 1e-5f); assertEquals(0.6f, m.bottom, 1e-5f)
        assertEquals(r.width, m.width, 1e-5f)
    }

    @Test fun corner_cannotCrossOppositeSide_orLeaveImage() {
        val tl = CropMath.drag(r, CropHandle.TOP_LEFT, 2f, 2f)
        assertEquals(0.8f - CropMath.MIN_SIZE, tl.left, 1e-5f)
        assertEquals(0.8f - CropMath.MIN_SIZE, tl.top, 1e-5f)
        val br = CropMath.drag(r, CropHandle.BOTTOM_RIGHT, 2f, 2f)
        assertEquals(1f, br.right, 1e-5f); assertEquals(1f, br.bottom, 1e-5f)
        val bl = CropMath.drag(r, CropHandle.BOTTOM_LEFT, -2f, -2f)
        assertEquals(0f, bl.left, 1e-5f); assertEquals(0.2f + CropMath.MIN_SIZE, bl.bottom, 1e-5f)
        val tr = CropMath.drag(r, CropHandle.TOP_RIGHT, -2f, -2f)
        assertEquals(0.2f + CropMath.MIN_SIZE, tr.right, 1e-5f); assertEquals(0f, tr.top, 1e-5f)
    }

    @Test fun toPixels_staysInsideImage_andAtLeastOnePixel() {
        val px = CropMath.toPixels(NormalizedRect.Full, 1000, 600)
        assertEquals(listOf(0, 0, 1000, 600), px.toList())
        val tiny = CropMath.toPixels(NormalizedRect(0.9999f, 0.9999f, 1f, 1f), 100, 100)
        assertTrue(tiny[2] >= 1 && tiny[3] >= 1 && tiny[0] + tiny[2] <= 100 && tiny[1] + tiny[3] <= 100)
    }

    @Test fun hitTest_cornersBeatMove_andOutsideIsNull() {
        // 1000x500 view; rect corners at (200,100) (800,100) (200,400) (800,400)
        assertEquals(CropHandle.TOP_LEFT, CropMath.hitTest(r, 210f, 110f, 1000f, 500f, 40f))
        assertEquals(CropHandle.BOTTOM_RIGHT, CropMath.hitTest(r, 790f, 390f, 1000f, 500f, 40f))
        assertEquals(CropHandle.MOVE, CropMath.hitTest(r, 500f, 250f, 1000f, 500f, 40f))
        assertEquals(null, CropMath.hitTest(r, 20f, 20f, 1000f, 500f, 40f))
    }
}
