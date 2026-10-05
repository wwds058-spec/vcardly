package com.yasin.vcardly.domain.vcard

import java.nio.charset.Charset

/**
 * Tolerant vCard reader (2.1, 3.0, 4.0 flavours): unfolds lines, handles quoted-printable and groups,
 * ignores what it does not understand (photos, birthdays, custom X- fields). Never throws on bad input.
 */
object VCardParser {
    const val MAX_CARDS = 5000

    fun parse(input: String): List<ParsedVCard> {
        val lines = unfold(input.removePrefix("\uFEFF"))
        val cards = mutableListOf<ParsedVCard>()
        var props: MutableList<Prop>? = null
        for (line in lines) {
            val upper = line.trim().uppercase()
            when {
                upper == "BEGIN:VCARD" -> props = mutableListOf()
                upper == "END:VCARD" -> {
                    props?.let { cards += build(it) }
                    props = null
                    if (cards.size >= MAX_CARDS) return cards
                }
                props != null -> parseProp(line)?.let { props.add(it) }
            }
        }
        return cards.filter { it != ParsedVCard() }
    }

    private class Prop(val name: String, val types: Set<String>, val value: String)

    /** RFC folding (continuation lines start with space/tab) plus quoted-printable soft breaks (line ends with '='). */
    private fun unfold(text: String): List<String> {
        val raw = text.split(Regex("\r\n|\n|\r"))
        val unfolded = mutableListOf<String>()
        for (line in raw) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] = unfolded.last() + line.substring(1)
            } else unfolded += line
        }
        val joined = mutableListOf<String>()
        var i = 0
        while (i < unfolded.size) {
            var line = unfolded[i]
            val isQp = line.contains("QUOTED-PRINTABLE", ignoreCase = true)
            while (isQp && line.endsWith("=") && i + 1 < unfolded.size) {
                line = line.dropLast(1) + unfolded[++i]
            }
            joined += line
            i++
        }
        return joined
    }

    private fun parseProp(line: String): Prop? {
        // Split "NAME;PARAMS:VALUE" at the first colon outside double quotes.
        var inQuotes = false
        var colon = -1
        for ((idx, ch) in line.withIndex()) {
            if (ch == '"') inQuotes = !inQuotes
            if (ch == ':' && !inQuotes) { colon = idx; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        var value = line.substring(colon + 1)
        val parts = head.split(';')
        val name = parts[0].substringAfterLast('.').trim().uppercase() // drop "item1." group prefix
        val types = mutableSetOf<String>()
        var qp = false
        var charset: Charset = Charsets.UTF_8
        parts.drop(1).forEach { param ->
            val key = param.substringBefore('=', "").trim().uppercase()
            val v = param.substringAfter('=', param).trim().trim('"')
            when (key) {
                "TYPE" -> v.split(',').forEach { types += it.trim().uppercase() }
                "ENCODING" -> if (v.equals("QUOTED-PRINTABLE", true) || v.equals("Q", true)) qp = true
                "CHARSET" -> runCatching { charset = Charset.forName(v) }
                "" -> { // bare v2.1 parameter such as TEL;CELL;VOICE
                    val bare = param.trim().uppercase()
                    if (bare == "QUOTED-PRINTABLE") qp = true else if (bare.isNotEmpty()) types += bare
                }
            }
        }
        if (qp) value = decodeQuotedPrintable(value, charset)
        return Prop(name, types, value)
    }

    private fun build(props: List<Prop>): ParsedVCard {
        fun first(name: String) = props.firstOrNull { it.name == name }

        val formatted = first("FN")?.let { unescape(it.value).trim() }.orEmpty()
        val structured = first("N")?.let { p ->
            val c = splitUnescaped(p.value, ';').map { it.trim() }
            // family;given;additional;prefix;suffix
            listOf(c.getOrNull(3), c.getOrNull(1), c.getOrNull(2), c.getOrNull(0), c.getOrNull(4))
                .filter { !it.isNullOrBlank() }.joinToString(" ")
        }.orEmpty()

        val company = first("ORG")?.let { splitUnescaped(it.value, ';').firstOrNull { c -> c.isNotBlank() } }.orEmpty()

        val phones = props.filter { it.name == "TEL" && "FAX" !in it.types }
            .sortedBy { if ("CELL" in it.types || "MOBILE" in it.types) 0 else 1 }
            .map { it.value.trim().removePrefix("tel:").removePrefix("TEL:").trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val emails = props.filter { it.name == "EMAIL" }
            .map { it.value.trim().removePrefix("mailto:").trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

        val address = props.firstOrNull { it.name == "ADR" }?.let { p ->
            splitUnescaped(p.value, ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        }.orEmpty()

        val categories = props.filter { it.name == "CATEGORIES" }
            .flatMap { splitUnescaped(it.value, ',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

        return ParsedVCard(
            fullName = formatted.ifBlank { structured },
            jobTitle = first("TITLE")?.let { unescape(it.value).trim() }.orEmpty(),
            company = company,
            phones = phones,
            emails = emails,
            website = first("URL")?.value?.trim().orEmpty(),
            address = address,
            notes = first("NOTE")?.let { unescape(it.value).trim() }.orEmpty(),
            categories = categories,
        )
    }

    /** Splits on [sep] unless escaped with a backslash, then unescapes each piece. */
    internal fun splitUnescaped(value: String, sep: Char): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                ch == '\\' && i + 1 < value.length -> { cur.append(ch).append(value[i + 1]); i++ }
                ch == sep -> { parts += unescape(cur.toString()); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        parts += unescape(cur.toString())
        return parts
    }

    internal fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(n) // \\ \; \,
                }
                i += 2
            } else { out.append(ch); i++ }
        }
        return out.toString()
    }

    internal fun decodeQuotedPrintable(value: String, charset: Charset): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '=' && i + 2 < value.length && value.substring(i + 1, i + 3).all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
                bytes.write(value.substring(i + 1, i + 3).toInt(16)); i += 3
            } else {
                bytes.write(ch.toString().toByteArray(charset)); i++
            }
        }
        return String(bytes.toByteArray(), charset)
    }
}
