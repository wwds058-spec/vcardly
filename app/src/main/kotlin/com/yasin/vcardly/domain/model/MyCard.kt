package com.yasin.vcardly.domain.model

import com.yasin.vcardly.domain.vcard.ShareCard
import com.yasin.vcardly.domain.vcard.ShareField

/** The user's own business card (what they hand out). Stored on this device only. */
data class MyCard(
    val fullName: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val phoneAlt: String = "",
    val email: String = "",
    val emailAlt: String = "",
    val website: String = "",
    val address: String = "",
) {
    val isEmpty: Boolean get() = fullName.isBlank()

    fun toShareCard() = ShareCard(
        mapOf(
            ShareField.NAME to fullName, ShareField.JOB_TITLE to jobTitle, ShareField.COMPANY to company,
            ShareField.PHONE to phone, ShareField.PHONE_ALT to phoneAlt, ShareField.EMAIL to email,
            ShareField.EMAIL_ALT to emailAlt, ShareField.WEBSITE to website, ShareField.ADDRESS to address,
        ),
    )
}
