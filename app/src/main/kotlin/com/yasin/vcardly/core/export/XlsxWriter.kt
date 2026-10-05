package com.yasin.vcardly.core.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free .xlsx writer (Office Open XML). Every cell is an inline string, so nothing a contact
 * contains can ever be interpreted as a formula. Header row is bold and frozen; column widths fit the content.
 */
object XlsxWriter {
    private const val MAX_CELL = 32_000 // Excel's hard limit is 32,767 characters
    private const val MAX_WIDTH = 60
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun write(tables: List<Table>, out: OutputStream) {
        require(tables.isNotEmpty()) { "at least one sheet" }
        val names = uniqueSheetNames(tables.map { it.name })
        ZipOutputStream(out).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n$xml").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(tables.size))
            put("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            put("xl/workbook.xml", "<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\"><sheets>" +
                names.mapIndexed { i, n -> "<sheet name=\"${esc(n)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") +
                "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                tables.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
                "<Relationship Id=\"rId${tables.size + 1}\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/></Relationships>")
            put("xl/styles.xml", styles())
            tables.forEachIndexed { i, t -> put("xl/worksheets/sheet${i + 1}.xml", sheet(t)) }
        }
    }

    fun toBytes(tables: List<Table>): ByteArray = java.io.ByteArrayOutputStream().also { write(tables, it) }.toByteArray()

    private fun contentTypes(sheets: Int) =
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            (1..sheets).joinToString("") { "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } +
            "</Types>"

    /** cellXfs 0 = normal, 1 = bold header. */
    private fun styles() =
        "<styleSheet xmlns=\"$MAIN_NS\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"

    private fun sheet(t: Table): String {
        val all = listOf(t.header) + t.rows
        val cols = t.header.size
        val widths = (0 until cols).map { c -> (all.maxOf { r -> r.getOrNull(c).orEmpty().lineSequence().maxOfOrNull { it.length } ?: 0 } + 2).coerceIn(8, MAX_WIDTH) }
        val sb = StringBuilder("<worksheet xmlns=\"$MAIN_NS\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        sb.append("<cols>").append(widths.mapIndexed { i, w -> "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>" }.joinToString("")).append("</cols>")
        sb.append("<sheetData>")
        all.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, value ->
                if (value.isNotEmpty()) {
                    val style = if (r == 0) " s=\"1\"" else ""
                    sb.append("<c r=\"${column(c)}${r + 1}\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${esc(value.take(MAX_CELL))}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    internal fun column(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do { sb.append('A' + n % 26); n = n / 26 - 1 } while (n >= 0)
        return sb.reverse().toString()
    }

    /** Sheet names: max 31 chars, none of []:*?/\ , not blank, unique ignoring case. */
    internal fun uniqueSheetNames(raw: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return raw.mapIndexed { i, name ->
            var base = name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(31).ifEmpty { "Sheet${i + 1}" }
            var candidate = base
            var n = 2
            while (!used.add(candidate.lowercase())) {
                val suffix = " ($n)"; candidate = base.take(31 - suffix.length) + suffix; n++
            }
            candidate
        }
    }

    /** Escapes XML and removes characters that are illegal in XML 1.0 (they would corrupt the whole file). */
    internal fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val legal = cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF
            if (!legal) continue
            when (cp) {
                '&'.code -> sb.append("&amp;"); '<'.code -> sb.append("&lt;"); '>'.code -> sb.append("&gt;")
                '"'.code -> sb.append("&quot;"); '\''.code -> sb.append("&apos;")
                else -> sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }
}
