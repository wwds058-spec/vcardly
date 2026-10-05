package com.yasin.vcardly.core.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.yasin.vcardly.domain.vcard.VCardData
import com.yasin.vcardly.domain.vcard.VCardPhone
import com.yasin.vcardly.domain.vcard.VCardParser
import com.yasin.vcardly.domain.vcard.VCardWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrEncoderTest {
    /** Renders the matrix like a screen would (module = 6px, 4-module white border) and decodes it back with ZXing. */
    private fun decode(m: QrMatrix): String {
        val scale = 6; val quiet = 4
        val side = (m.size + quiet * 2) * scale
        val px = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until m.size) for (x in 0 until m.size) if (m[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale) px[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, px)))).text
    }

    @Test fun vCardPayload_roundTripsThroughAQrCode() {
        val payload = VCardWriter.write(VCardData("Asha Rao", "CTO", "Acme", listOf(VCardPhone("+91 98765 43210", "CELL")), listOf("asha@acme.com"), "https://acme.com"))
        val decoded = decode(QrEncoder.encode(payload)!!)
        assertEquals(payload, decoded)
        assertEquals("Asha Rao", VCardParser.parse(decoded).single().fullName)
    }

    @Test fun unicodePayload_roundTrips() {
        val payload = VCardWriter.write(VCardData("రమేష్ కుమార్", company = "اردو"))
        assertEquals(payload, decode(QrEncoder.encode(payload)!!))
    }

    @Test fun tooMuchData_returnsNullInsteadOfThrowing() {
        assertNull(QrEncoder.encode("x".repeat(5000)))
    }

    @Test fun emptyText_returnsNull() = assertNull(QrEncoder.encode(""))

    @Test fun matrixIsSquareAndNonEmpty() {
        val m = QrEncoder.encode("hello")!!
        assertNotNull(m); assertEquals(true, m.size >= 21)
    }
}
