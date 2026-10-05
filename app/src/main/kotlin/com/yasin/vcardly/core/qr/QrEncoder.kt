package com.yasin.vcardly.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Square grid of modules (true = dark). No quiet zone: the renderer adds it. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

/** Pure-JVM QR generation (ZXing core). Works fully offline. */
object QrEncoder {
    /**
     * Encodes [text] as UTF-8. Tries error-correction M first (survives smudges) and falls back to L to fit
     * more data. Returns null when the text is too long for any QR code.
     */
    fun encode(text: String): QrMatrix? {
        if (text.isEmpty()) return null
        for (level in listOf(ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)) {
            try {
                val hints = mapOf(
                    EncodeHintType.ERROR_CORRECTION to level,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 0,
                )
                val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
                val bits = BooleanArray(m.width * m.height) { i -> m.get(i % m.width, i / m.width) }
                return QrMatrix(m.width, bits)
            } catch (_: WriterException) {
                // too much data at this level; try the next
            }
        }
        return null
    }
}
