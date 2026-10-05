package com.yasin.vcardly.domain.vcard

import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.domain.usecase.ContactValidator

enum class ImportStatus { NEW, DUPLICATE, UNUSABLE }

/** One card from a file, ready to import. [contact] is null only when [status] is UNUSABLE. */
data class ImportCandidate(
    val index: Int,
    val status: ImportStatus,
    val contact: Contact?,
    val tags: List<String>,
)

object DuplicateDetector {
    /**
     * Same email (case-insensitive), or same phone (last 9 digits, so +91 prefixes do not matter),
     * or same name + company.
     */
    fun isDuplicate(candidate: Contact, existing: List<Contact>): Boolean {
        val emails = emails(candidate)
        val phones = phones(candidate)
        val identity = identity(candidate)
        return existing.any { e ->
            (emails.isNotEmpty() && emails.any { it in emails(e) }) ||
                (phones.isNotEmpty() && phones.any { it in phones(e) }) ||
                (identity != null && identity == identity(e))
        }
    }

    private fun emails(c: Contact) = listOf(c.email, c.emailAlt).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    private fun phones(c: Contact) = listOf(c.phone, c.phoneAlt)
        .map { p -> p.filter(Char::isDigit) }.filter { it.length >= 7 }.map { it.takeLast(9) }.toSet()

    private fun identity(c: Contact): String? {
        val name = c.fullName.trim().lowercase().replace(Regex("\\s+"), " ")
        return if (name.isEmpty()) null else name + "|" + c.company.trim().lowercase()
    }
}

object VCardImporter {
    /**
     * Turns parsed cards into contacts without losing data: a value that would fail form validation (an odd phone
     * format, a long note) is kept in the notes instead of being dropped or blocking the import.
     * Duplicates are detected against [existing] and against earlier cards in the same file.
     */
    fun prepare(parsed: List<ParsedVCard>, existing: List<Contact>): List<ImportCandidate> {
        val accepted = mutableListOf<Contact>()
        return parsed.mapIndexed { index, p ->
            val contact = toContact(p)
            when {
                contact == null -> ImportCandidate(index, ImportStatus.UNUSABLE, null, emptyList())
                DuplicateDetector.isDuplicate(contact, existing + accepted) ->
                    ImportCandidate(index, ImportStatus.DUPLICATE, contact, p.categories)
                else -> { accepted += contact; ImportCandidate(index, ImportStatus.NEW, contact, p.categories) }
            }
        }
    }

    internal fun toContact(p: ParsedVCard): Contact? {
        val name = p.fullName.trim().ifBlank { p.company.trim() }.ifBlank { p.emails.firstOrNull()?.substringBefore('@').orEmpty() }
        if (name.isBlank()) return null

        val overflow = mutableListOf<String>()
        fun fit(value: String, max: Int): String {
            val v = value.trim()
            if (v.length <= max) return v
            overflow += v
            return v.take(max)
        }
        var phone = p.phones.getOrNull(0).orEmpty()
        var phoneAlt = p.phones.getOrNull(1).orEmpty()
        var email = p.emails.getOrNull(0).orEmpty()
        var emailAlt = p.emails.getOrNull(1).orEmpty()
        var website = p.website.trim()

        // Keep anything the form would reject, in notes, so nothing silently disappears.
        val probe = Contact(fullName = name, phone = phone, phoneAlt = phoneAlt, email = email, emailAlt = emailAlt, website = website)
        val bad = ContactValidator.validate(probe).keys
        fun rescue(field: ContactField, value: String): String {
            if (field in bad && value.isNotBlank()) { overflow += value; return "" }
            return value
        }
        phone = rescue(ContactField.PHONE, phone); phoneAlt = rescue(ContactField.PHONE_ALT, phoneAlt)
        email = rescue(ContactField.EMAIL, email); emailAlt = rescue(ContactField.EMAIL_ALT, emailAlt)
        website = rescue(ContactField.WEBSITE, website)
        // Extra numbers / addresses beyond the two slots.
        p.phones.drop(2).forEach { overflow += it }
        p.emails.drop(2).forEach { overflow += it }

        val extra = overflow.distinct().joinToString("\n")
        val notes = listOf(p.notes.trim(), extra).filter { it.isNotEmpty() }.joinToString("\n").take(ContactValidator.MAX_NOTES)

        return Contact(
            fullName = name.take(ContactValidator.MAX_NAME),
            jobTitle = fit(p.jobTitle, ContactValidator.MAX_SHORT),
            company = fit(p.company, ContactValidator.MAX_SHORT),
            phone = phone, phoneAlt = phoneAlt, email = email, emailAlt = emailAlt, website = website,
            address = fit(p.address, ContactValidator.MAX_ADDRESS),
            notes = notes,
            source = ContactSource.IMPORT,
        )
    }
}
