package com.yasin.vcardly.domain.usecase

import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.model.FollowUp

enum class FollowUpField { CONTACT, TITLE, NOTES, DUE }

object FollowUpValidator {
    const val MAX_TITLE = 100
    const val MAX_NOTES = 1000

    fun validate(followUp: FollowUp): Map<FollowUpField, Reason> = buildMap {
        if (followUp.contactId <= 0L) put(FollowUpField.CONTACT, Reason.REQUIRED)
        val title = followUp.title.trim()
        when {
            title.isEmpty() -> put(FollowUpField.TITLE, Reason.REQUIRED)
            title.length > MAX_TITLE -> put(FollowUpField.TITLE, Reason.TOO_LONG)
        }
        if (followUp.notes.trim().length > MAX_NOTES) put(FollowUpField.NOTES, Reason.TOO_LONG)
        if (followUp.dueAt <= 0L) put(FollowUpField.DUE, Reason.REQUIRED)
    }
}
