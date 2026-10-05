package com.yasin.vcardly.domain.vcard

import com.yasin.vcardly.domain.model.Contact

/** Fields a user can choose to share. Private notes are deliberately not shareable. */
enum class ShareField { NAME, JOB_TITLE, COMPANY, PHONE, PHONE_ALT, EMAIL, EMAIL_ALT, WEBSITE, ADDRESS }

/** The values of a card that can be shared (either the user's own card or a saved contact). */
data class ShareCard(val values: Map<ShareField, String>) {
    /** Only fields that actually have a value are offered in the picker. */
    val available: List<ShareField> = ShareField.entries.filter { !values[it].isNullOrBlank() }

    /** Default selection: the usual business-card fields that exist. NAME is always on. */
    val defaultSelection: Set<ShareField> =
        setOf(ShareField.NAME, ShareField.JOB_TITLE, ShareField.COMPANY, ShareField.PHONE, ShareField.EMAIL, ShareField.WEBSITE)
            .filter { it in available || it == ShareField.NAME }.toSet()

    /** vCard with only [selected] fields. NAME is always included because a vCard needs a name. */
    fun toVCard(selected: Set<ShareField>): VCardData {
        fun v(f: ShareField) = if (f in selected || f == ShareField.NAME) values[f].orEmpty().trim() else ""
        val phones = listOf(
            VCardPhone(v(ShareField.PHONE), "CELL"),
            VCardPhone(v(ShareField.PHONE_ALT), "WORK"),
        ).filter { it.number.isNotEmpty() }
        return VCardData(
            fullName = v(ShareField.NAME),
            jobTitle = v(ShareField.JOB_TITLE),
            company = v(ShareField.COMPANY),
            phones = phones,
            emails = listOf(v(ShareField.EMAIL), v(ShareField.EMAIL_ALT)).filter { it.isNotEmpty() },
            website = v(ShareField.WEBSITE),
            address = v(ShareField.ADDRESS),
        )
    }

    companion object {
        fun of(contact: Contact) = ShareCard(
            mapOf(
                ShareField.NAME to contact.fullName, ShareField.JOB_TITLE to contact.jobTitle,
                ShareField.COMPANY to contact.company, ShareField.PHONE to contact.phone,
                ShareField.PHONE_ALT to contact.phoneAlt, ShareField.EMAIL to contact.email,
                ShareField.EMAIL_ALT to contact.emailAlt, ShareField.WEBSITE to contact.website,
                ShareField.ADDRESS to contact.address,
            ),
        )
    }
}
