package com.yasin.vcardly.domain.vcard

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Writes vCard 3.0 (the most widely accepted version) with CRLF line ends and 75-octet folding. */
object VCardWriter {
    private val revFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun write(cards: List<VCardData>, revision: Instant? = null): String =
        cards.joinToString(separator = "") { card(it, revision) }

    fun write(card: VCardData, revision: Instant? = null): String = card(card, revision)

    private fun card(c: VCardData, revision: Instant?): String {
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
        lines += "FN:${text(c.fullName)}"
        lines += "N:${structuredName(c.fullName)}"
        if (c.company.isNotBlank()) lines += "ORG:${text(c.company)}"
        if (c.jobTitle.isNotBlank()) lines += "TITLE:${text(c.jobTitle)}"
        c.phones.filter { it.number.isNotBlank() }.forEach { p ->
            lines += "TEL;TYPE=${p.type ?: "VOICE"}:${plain(p.number)}"
        }
        c.emails.filter { it.isNotBlank() }.forEach { lines += "EMAIL;TYPE=INTERNET:${plain(it)}" }
        if (c.website.isNotBlank()) lines += "URL:${plain(c.website)}"
        // ADR = PO box;extended;street;city;region;postal code;country. We only hold free text, so it goes in "street".
        if (c.address.isNotBlank()) lines += "ADR;TYPE=WORK:;;${text(c.address)};;;;"
        if (c.notes.isNotBlank()) lines += "NOTE:${text(c.notes)}"
        val cats = c.categories.filter { it.isNotBlank() }
        if (cats.isNotEmpty()) lines += "CATEGORIES:${cats.joinToString(",") { text(it) }}"
        if (revision != null) lines += "REV:${revFormat.format(revision)}"
        lines += "END:VCARD"
        return lines.joinToString(separator = "") { fold(it) + "\r\n" }
    }

    /** Last word = family name, the rest = given names. A single word is a given name. */
    private fun structuredName(fullName: String): String {
        val words = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> ";;;;"
            words.size == 1 -> ";${text(words[0])};;;"
            else -> "${text(words.last())};${text(words.dropLast(1).joinToString(" "))};;;"
        }
    }

    /** TEXT value: escape backslash, semicolon, comma and newlines. */
    internal fun text(value: String): String = value.trim()
        .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    /** URI / phone / email values are not TEXT: only line breaks must go. */
    private fun plain(value: String): String = value.trim().replace(Regex("[\\r\\n]+"), " ")

    /** Folds so no line exceeds 75 UTF-8 octets; never splits a multi-byte character. */
    internal fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val out = StringBuilder()
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val chars = Character.charCount(cp)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                out.append("\r\n ")
                octets = 0
                limit = 74 // the leading space counts toward the 75
            }
            out.appendCodePoint(cp)
            octets += size
            i += chars
        }
        return out.toString()
    }
}
