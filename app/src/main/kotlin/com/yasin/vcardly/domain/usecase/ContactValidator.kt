package com.yasin.vcardly.domain.usecase

import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.model.Contact

enum class ContactField { FULL_NAME, JOB_TITLE, COMPANY, PHONE, PHONE_ALT, EMAIL, EMAIL_ALT, WEBSITE, ADDRESS, NOTES }

/**
 * Form validation, pure Kotlin. Every optional field may be blank; a non-blank value must be valid.
 * Rules are deliberately lenient about *style* (OCR and international cards vary) and strict only
 * about things that would break actions like dialling or mailing.
 *
 * Phone: digits, spaces and + ( ) - . only, 5 to 15 digits (E.164 maximum).
 */
object ContactValidator {
    const val MAX_NAME = 100
    const val MAX_SHORT = 100
    const val MAX_PHONE = 30
    const val MAX_EMAIL = 254
    const val MAX_WEBSITE = 200
    const val MAX_ADDRESS = 300
    const val MAX_NOTES = 2000

    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}$")
    private val phoneChars = Regex("^[0-9+()\\-. ]+$")
    private val websiteRegex =
        Regex("^(https?://)?([\\p{L}\\p{N}-]+\\.)+[\\p{L}\\p{N}-]{2,}(:\\d+)?([/?#]\\S*)?$", RegexOption.IGNORE_CASE)

    fun validate(contact: Contact): Map<ContactField, Reason> = buildMap {
        val name = contact.fullName.trim()
        when {
            name.isEmpty() -> put(ContactField.FULL_NAME, Reason.REQUIRED)
            name.length > MAX_NAME -> put(ContactField.FULL_NAME, Reason.TOO_LONG)
        }
        checkLength(ContactField.JOB_TITLE, contact.jobTitle, MAX_SHORT)
        checkLength(ContactField.COMPANY, contact.company, MAX_SHORT)
        checkLength(ContactField.ADDRESS, contact.address, MAX_ADDRESS)
        checkLength(ContactField.NOTES, contact.notes, MAX_NOTES)
        checkPhone(ContactField.PHONE, contact.phone)
        checkPhone(ContactField.PHONE_ALT, contact.phoneAlt)
        checkEmail(ContactField.EMAIL, contact.email)
        checkEmail(ContactField.EMAIL_ALT, contact.emailAlt)
        val site = contact.website.trim()
        if (site.isNotEmpty()) {
            when {
                site.length > MAX_WEBSITE -> put(ContactField.WEBSITE, Reason.TOO_LONG)
                !websiteRegex.matches(site) -> put(ContactField.WEBSITE, Reason.INVALID_FORMAT)
            }
        }
    }

    private fun MutableMap<ContactField, Reason>.checkLength(field: ContactField, value: String, max: Int) {
        if (value.trim().length > max) put(field, Reason.TOO_LONG)
    }

    private fun MutableMap<ContactField, Reason>.checkPhone(field: ContactField, raw: String) {
        val v = raw.trim()
        if (v.isEmpty()) return
        val digits = v.count { it.isDigit() }
        when {
            v.length > MAX_PHONE -> put(field, Reason.TOO_LONG)
            !phoneChars.matches(v) || digits !in 5..15 -> put(field, Reason.INVALID_FORMAT)
        }
    }

    private fun MutableMap<ContactField, Reason>.checkEmail(field: ContactField, raw: String) {
        val v = raw.trim()
        if (v.isEmpty()) return
        when {
            v.length > MAX_EMAIL -> put(field, Reason.TOO_LONG)
            !emailRegex.matches(v) -> put(field, Reason.INVALID_FORMAT)
        }
    }
}
