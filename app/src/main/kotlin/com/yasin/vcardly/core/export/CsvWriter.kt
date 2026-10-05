package com.yasin.vcardly.core.export

/**
 * RFC 4180 CSV (CRLF, quoted when needed) with protection against spreadsheet formula injection:
 * a contact's name such as `=HYPERLINK(...)` must not run when the file is opened in Excel/Sheets.
 */
object CsvWriter {
    /** UTF-8 byte-order mark; without it Excel mis-reads non-Latin text. Written once at the start of the file. */
    const val BOM = "\uFEFF"

    private val formulaStart = setOf('=', '+', '-', '@', '\t', '\r')
    /** Digits and phone punctuation only can never form a formula, so real phone numbers like "+91 98765 43210" stay intact. */
    private val phoneLike = Regex("^[+\\-]?[0-9 ()./\\-]+$")

    fun write(table: Table): String {
        val sb = StringBuilder()
        (listOf(table.header) + table.rows).forEach { row ->
            sb.append(row.joinToString(",") { cell(it) }).append("\r\n")
        }
        return sb.toString()
    }

    internal fun cell(raw: String): String {
        val safe = neutralise(raw)
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || safe.startsWith(" ") || safe.endsWith(" ")
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    internal fun neutralise(value: String): String =
        if (value.isNotEmpty() && value[0] in formulaStart && !phoneLike.matches(value)) "'$value" else value
}
