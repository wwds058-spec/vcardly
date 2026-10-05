package com.yasin.vcardly.domain.vcard

data class VCardPhone(val number: String, val type: String? = null)

/** Format-level contact data, independent of how VCardly stores contacts. */
data class VCardData(
    val fullName: String,
    val jobTitle: String = "",
    val company: String = "",
    val phones: List<VCardPhone> = emptyList(),
    val emails: List<String> = emptyList(),
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val categories: List<String> = emptyList(),
)

/** What the reader found in a card. Phones are ordered mobile-first with fax numbers removed. */
data class ParsedVCard(
    val fullName: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val categories: List<String> = emptyList(),
)
