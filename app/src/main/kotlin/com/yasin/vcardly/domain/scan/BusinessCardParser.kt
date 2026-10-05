package com.yasin.vcardly.domain.scan

/**
 * Heuristic field detection for business-card OCR. Pure Kotlin, no logging, no storage: the text is PII.
 *
 * Order matters: unambiguous patterns first (email, phone, website), then keyword classes
 * (job title, company, address), and only then the name, chosen from what is left by size/position.
 */
object BusinessCardParser {
    private class Work(var text: String, val top: Int, val height: Int, var used: Boolean = false)

    private val emailRegex = Regex("[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+\\.\\p{L}{2,}")
    private const val TLDS = "com|net|org|edu|gov|io|co|in|biz|info|app|dev|ai|me|us|uk|de|fr|au|ca|ae|sg|tech|online|store|xyz|pro|tv|cc|eu|asia"
    private val urlRegex = Regex(
        "(?<![@\\w.])(?:https?://)?(?:www\\.)?(?:[\\p{L}\\p{N}][\\p{L}\\p{N}\\-]*\\.)+(?:$TLDS)\\b(?:/[^\\s,;]*)?",
        RegexOption.IGNORE_CASE,
    )
    private val phoneRegex = Regex("(?<!\\d)\\+?\\(?\\d[\\d\\s().\\-]{5,}\\d(?!\\d)")

    /** Splits "T: 1 M: 2" into separate segments at colon-labels. */
    private val segmentSplit = Regex("\\s+(?=(?:tel|phone|ph|mob|mobile|cell|fax|f|t|m|e|w|o|d)\\s*:)", RegexOption.IGNORE_CASE)
    private val labelPrefix = Regex(
        "^(?:(?:e-?mail|email|web(?:site)?|url|telephone|tel|phone|ph|mobile|mob|cell|fax|direct|office|address|addr)\\s*[:.\\-–]?\\s+|(?:[a-z])\\s*:\\s*)",
        RegexOption.IGNORE_CASE,
    )
    private val faxLine = Regex("^\\s*(?:fax|f)\\b\\s*[:.\\-]?", RegexOption.IGNORE_CASE)
    private val mobileLine = Regex("^\\s*(?:mobile|mob|cell|m)\\b\\s*[:.\\-]?", RegexOption.IGNORE_CASE)

    private val titleRegex = Regex(
        "\\b(?:manager|director|ceo|cto|cfo|coo|cmo|founder|co-?founder|engineer|developer|president|vice|vp|head|lead|" +
            "consultant|officer|partner|associate|analyst|designer|executive|sales|marketing|owner|proprietor|principal|" +
            "architect|specialist|supervisor|chairman|chairperson|md|advisor|adviser|manager|coordinator|administrator|" +
            "attorney|lawyer|doctor|professor|scientist|researcher|recruiter|accountant)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val companyRegex = Regex(
        "\\b(?:pvt|private|ltd|limited|llc|llp|inc|incorporated|corp|corporation|co|company|gmbh|plc|technologies|technology|" +
            "solutions|systems|group|industries|enterprises|associates|studio|studios|labs|consulting|services|" +
            "software|holdings|ventures|partners|agency|foundation|university|institute|bank)\\b\\.?",
        RegexOption.IGNORE_CASE,
    )
    private val addressRegex = Regex(
        "\\b(?:street|st|road|rd|avenue|ave|lane|ln|floor|flr|suite|ste|block|plot|nagar|colony|sector|phase|building|" +
            "bldg|tower|plaza|marg|chowk|highway|hwy|boulevard|blvd|city|district|po box|p\\.o\\.)\\b\\.?",
        RegexOption.IGNORE_CASE,
    )
    private val postalRegex = Regex("(?<!\\d)\\d{5,6}(?!\\d)")
    private val nameCharsOnly = Regex("^[\\p{L}][\\p{L}.'\\-\\s]*$")
    private val trimJunk = Regex("^[\\s,;:|•·\\-–—/]+|[\\s,;:|•·\\-–—/]+$")
    private val genericMailHosts = setOf("gmail", "yahoo", "outlook", "hotmail", "icloud", "proton", "protonmail", "live", "aol", "rediffmail")

    fun parse(lines: List<OcrLine>): ParsedCard {
        val work = lines
            .flatMap { l -> l.text.trim().split(segmentSplit).map { Work(it.replace(Regex("\\s+"), " ").trim(), l.top, l.height) } }
            .filter { it.text.isNotEmpty() }
        if (work.isEmpty()) return ParsedCard()

        val emails = extractEmails(work)
        val websites = extractWebsites(work)
        val phones = extractPhones(work)

        // Whatever is left of lines after pulling out contact patterns.
        work.forEach { w ->
            w.text = w.text.replace(labelPrefix, "").replace(trimJunk, "")
            if (w.text.none { it.isLetterOrDigit() }) w.used = true
        }
        fun free() = work.filter { !it.used }

        val title = free().firstOrNull { titleRegex.containsMatchIn(it.text) }?.also { it.used = true }

        var company = free().firstOrNull { companyRegex.containsMatchIn(it.text) && it !== title }?.also { it.used = true }
        if (company == null) {
            val domains = (emails.map { it.substringAfter('@') } + websites)
                .map { domainLabel(it) }.filter { it.length >= 3 && it !in genericMailHosts }
            company = domains.firstNotNullOfOrNull { label ->
                free().firstOrNull { it.text.contains(label, ignoreCase = true) && it.text.any(Char::isLetter) }
            }?.also { it.used = true }
        }

        val addressLines = free().filter { isAddress(it.text) }.onEach { it.used = true }

        val name = pickName(free(), emails.firstOrNull())?.also { it.used = true }

        if (company == null) {
            // Last resort: the most prominent remaining text line that is not the name.
            company = free().filter { it.text.any(Char::isLetter) && !it.text.any(Char::isDigit) }
                .maxByOrNull { it.height }?.also { it.used = true }
        }

        return ParsedCard(
            fullName = name?.text.orEmpty(),
            jobTitle = title?.text.orEmpty(),
            company = company?.text.orEmpty(),
            phone = phones.getOrElse(0) { "" },
            phoneAlt = phones.getOrElse(1) { "" },
            email = emails.getOrElse(0) { "" },
            emailAlt = emails.getOrElse(1) { "" },
            website = websites.firstOrNull().orEmpty(),
            address = addressLines.joinToString(", ") { it.text.trimEnd(',', ' ') },
            unmatched = free().map { it.text },
        )
    }

    private fun extractEmails(work: List<Work>): List<String> {
        val found = mutableListOf<String>()
        work.forEach { w ->
            emailRegex.findAll(w.text).forEach { found += it.value.trimEnd('.', ',') }
            w.text = w.text.replace(emailRegex, " ").trim()
        }
        return found.distinctBy { it.lowercase() }
    }

    private fun extractWebsites(work: List<Work>): List<String> {
        val found = mutableListOf<String>()
        work.forEach { w ->
            urlRegex.findAll(w.text).forEach { found += it.value.trimEnd('.', ',') }
            w.text = w.text.replace(urlRegex, " ").trim()
        }
        // Prefer addresses that are explicitly web addresses.
        return found.distinctBy { it.lowercase() }
            .sortedBy { if (it.startsWith("http", true) || it.startsWith("www.", true)) 0 else 1 }
    }

    /** Mobile numbers first, fax numbers dropped (left in the text so they surface as unmatched). */
    private fun extractPhones(work: List<Work>): List<String> {
        val mobile = mutableListOf<String>()
        val other = mutableListOf<String>()
        work.forEach { w ->
            if (faxLine.containsMatchIn(w.text)) return@forEach
            val isMobile = mobileLine.containsMatchIn(w.text)
            val matches = phoneRegex.findAll(w.text).filter { m -> m.value.count(Char::isDigit) in 7..15 }.toList()
            matches.forEach { (if (isMobile) mobile else other) += it.value.trim() }
            var text = w.text
            matches.forEach { text = text.replace(it.value, " ") }
            w.text = text.trim()
        }
        return (mobile + other).distinct()
    }

    private fun isAddress(text: String): Boolean =
        postalRegex.containsMatchIn(text) ||
            addressRegex.containsMatchIn(text) ||
            (text.any(Char::isDigit) && text.contains(',') && text.any(Char::isLetter))

    private fun pickName(candidates: List<Work>, primaryEmail: String?): Work? {
        val eligible = candidates.filter { w ->
            val words = w.text.split(' ').filter { it.isNotEmpty() }
            nameCharsOnly.matches(w.text) && words.size in 1..4 && w.text.length <= 40
        }
        if (eligible.isEmpty()) return null
        val maxHeight = candidates.maxOf { it.height }.coerceAtLeast(1)
        val maxTop = candidates.maxOf { it.top }.coerceAtLeast(1)
        val emailTokens = primaryEmail?.substringBefore('@')?.lowercase()
            ?.split(Regex("[._\\-\\d]+"))?.filter { it.length >= 3 }.orEmpty()

        fun score(w: Work): Double {
            val words = w.text.split(' ').filter { it.isNotEmpty() }
            var s = when (words.size) { 2, 3 -> 3.0; 4 -> 1.0; else -> 0.0 }
            s += 4.0 * w.height / maxHeight
            s += 2.0 * (1.0 - w.top.toDouble() / maxTop)
            if (words.any { word -> emailTokens.any { it == word.lowercase().trim('.') } }) s += 5.0
            return s
        }
        // maxByOrNull keeps the first of equal scores, i.e. the earlier line.
        return eligible.maxByOrNull { score(it) }
    }

    private fun domainLabel(hostOrUrl: String): String =
        hostOrUrl.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('/').split('.').let { parts ->
                // acme.co.in -> acme ; mail.acme.com -> acme
                val tldCount = parts.reversed().takeWhile { it in setOf("com", "co", "in", "org", "net", "uk", "io", "ac", "edu", "gov") }.size
                parts.getOrElse(parts.size - tldCount - 1) { parts.first() }
            }
}
