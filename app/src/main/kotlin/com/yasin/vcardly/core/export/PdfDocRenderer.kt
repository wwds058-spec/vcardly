package com.yasin.vcardly.core.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import java.io.OutputStream
import java.text.NumberFormat

/**
 * Paginates a [PdfDoc] onto A4 pages with the platform [PdfDocument] (no third-party library). Text is truncated with an
 * ellipsis rather than overflowing; tables repeat their header on every page; right-to-left locales are mirrored.
 */
object PdfDocRenderer {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    private const val INK = 0xFF1B1B1F.toInt()
    private const val MUTED = 0xFF46464F.toInt()
    private const val ACCENT = 0xFF3949AB.toInt()
    private const val TRACK = 0xFFE3E1EC.toInt()

    fun render(doc: PdfDoc, out: OutputStream, rtl: Boolean) {
        val pdf = PdfDocument()
        try {
            Session(pdf, rtl).draw(doc)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private class Session(private val pdf: PdfDocument, private val rtl: Boolean) {
        private val number = NumberFormat.getInstance()
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f

        private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private val title = paint(20f, INK, bold = true)
        private val subtitle = paint(10f, MUTED)
        private val heading = paint(14f, INK, bold = true)
        private val body = paint(10.5f, INK)
        private val bodyBold = paint(10.5f, INK, bold = true)
        private val small = paint(9f, INK)
        private val smallBold = paint(9f, INK, bold = true)
        private val footer = paint(9f, MUTED)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun draw(doc: PdfDoc) {
            newPage()
            y += 20f
            text(doc.title, 0f, CONTENT_W, title); y += 16f
            text(doc.subtitle, 0f, CONTENT_W, subtitle); y += 10f
            doc.blocks.forEach(::block)
            finishPage()
        }

        private fun block(b: PdfBlock) {
            when (b) {
                is PdfBlock.Heading -> {
                    ensure(44f); y += 22f
                    text(b.text, 0f, CONTENT_W, heading); y += 6f
                    rule(); y += 6f
                }
                is PdfBlock.Paragraph -> { ensure(20f); y += 14f; text(b.text, 0f, CONTENT_W, body) }
                is PdfBlock.KeyValues -> b.rows.forEach { (k, v) ->
                    ensure(18f); y += 14f
                    text(k, 0f, CONTENT_W * 0.7f, body); textEnd(v, CONTENT_W * 0.3f, bodyBold); y += 3f
                }
                is PdfBlock.Bars -> b.rows.forEach { r ->
                    ensure(20f); y += 13f
                    val labelW = CONTENT_W * 0.38f
                    val barStart = CONTENT_W * 0.42f
                    val barW = CONTENT_W * 0.45f
                    text(r.label, 0f, labelW, body)
                    bar(barStart, barW, r.fraction)
                    textEnd(r.valueText, CONTENT_W * 0.12f, body); y += 5f
                }
                is PdfBlock.TableBlock -> table(b)
            }
        }

        private fun table(t: PdfBlock.TableBlock) {
            val total = t.weights.sum()
            val widths = t.weights.map { CONTENT_W * it / total }
            fun header() {
                y += 12f
                var x = 0f
                t.header.forEachIndexed { i, h -> text(h, x, widths[i] - 6f, smallBold); x += widths[i] }
                y += 4f; rule()
            }
            ensure(40f); header()
            t.rows.forEach { row ->
                if (y + 15f > PAGE_H - MARGIN - 16f) { newPage(); header() }
                y += 13f
                var x = 0f
                row.forEachIndexed { i, cell -> if (i < widths.size) { text(cell, x, widths[i] - 6f, small); x += widths[i] } }
                y += 2f
            }
        }

        /** Draws [s] starting [offset] from the start edge (left, or right in RTL), truncated to [maxW]. */
        private fun text(s: String, offset: Float, maxW: Float, p: TextPaint) {
            val t = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END).toString()
            if (rtl) { p.textAlign = Paint.Align.RIGHT; canvas.drawText(t, PAGE_W - MARGIN - offset, y, p) }
            else { p.textAlign = Paint.Align.LEFT; canvas.drawText(t, MARGIN + offset, y, p) }
        }

        /** Draws [s] flush with the end edge, inside a box of [boxW]. */
        private fun textEnd(s: String, boxW: Float, p: TextPaint) {
            val t = TextUtils.ellipsize(s, p, boxW, TextUtils.TruncateAt.END).toString()
            if (rtl) { p.textAlign = Paint.Align.LEFT; canvas.drawText(t, MARGIN, y, p) }
            else { p.textAlign = Paint.Align.RIGHT; canvas.drawText(t, PAGE_W - MARGIN, y, p) }
        }

        private fun bar(start: Float, width: Float, fraction: Float) {
            val top = y - 8f
            val w = width * fraction.coerceIn(0f, 1f)
            val l = if (rtl) PAGE_W - MARGIN - start - width else MARGIN + start
            fill.color = TRACK
            canvas.drawRoundRect(l, top, l + width, top + 7f, 3f, 3f, fill)
            if (w > 0f) {
                fill.color = ACCENT
                val fl = if (rtl) l + width - w else l
                canvas.drawRoundRect(fl, top, fl + w.coerceAtLeast(3f), top + 7f, 3f, 3f, fill)
            }
        }

        private fun rule() {
            fill.color = TRACK
            canvas.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + 0.8f, fill)
        }

        private fun ensure(h: Float) {
            if (y + h > PAGE_H - MARGIN - 16f) newPage()
        }

        private fun newPage() {
            finishPage()
            pageNo++
            val p = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        private fun finishPage() {
            val p = page ?: return
            footer.textAlign = Paint.Align.CENTER
            canvas.drawText(number.format(pageNo), PAGE_W / 2f, PAGE_H - 24f, footer)
            pdf.finishPage(p)
            page = null
        }
    }
}
