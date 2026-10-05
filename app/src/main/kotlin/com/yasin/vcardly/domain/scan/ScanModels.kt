package com.yasin.vcardly.domain.scan

/** One recognised line of text. [top] and [height] are pixel geometry used to guess prominence. */
data class OcrLine(val text: String, val top: Int = 0, val height: Int = 0)

/**
 * Best-effort guesses from OCR. NOTHING here is trusted: every value pre-fills an editable form and the
 * user must review it. [unmatched] are lines no rule claimed, offered to the user rather than discarded.
 */
data class ParsedCard(
    val fullName: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val phoneAlt: String = "",
    val email: String = "",
    val emailAlt: String = "",
    val website: String = "",
    val address: String = "",
    val unmatched: List<String> = emptyList(),
)
