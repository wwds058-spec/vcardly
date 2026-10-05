package com.yasin.vcardly.presentation.common

import androidx.annotation.StringRes
import com.yasin.vcardly.R
import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.usecase.ContactField

/** Maps a validation failure to a localized message. */
@StringRes
fun validationMessageRes(field: ContactField, reason: Reason): Int = when (reason) {
    Reason.REQUIRED -> R.string.error_required
    Reason.TOO_LONG -> R.string.error_too_long
    Reason.DUPLICATE -> R.string.error_duplicate
    Reason.INVALID_FORMAT -> when (field) {
        ContactField.EMAIL, ContactField.EMAIL_ALT -> R.string.error_invalid_email
        ContactField.PHONE, ContactField.PHONE_ALT -> R.string.error_invalid_phone
        ContactField.WEBSITE -> R.string.error_invalid_website
        else -> R.string.error_invalid
    }
}

/** For forms whose fields need no field-specific wording. */
@StringRes
fun validationMessageRes(reason: Reason): Int = when (reason) {
    Reason.REQUIRED -> R.string.error_required
    Reason.TOO_LONG -> R.string.error_too_long
    Reason.DUPLICATE -> R.string.error_duplicate
    Reason.INVALID_FORMAT -> R.string.error_invalid
}
