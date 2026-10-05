package com.yasin.vcardly.domain.usecase

import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.model.FollowUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpValidatorTest {
    private fun f(contact: Long = 1, title: String = "Call", due: Long = 1, notes: String = "") =
        FollowUp(contactId = contact, title = title, dueAt = due, notes = notes)

    @Test fun valid() = assertTrue(FollowUpValidator.validate(f()).isEmpty())
    @Test fun missingContact() = assertEquals(Reason.REQUIRED, FollowUpValidator.validate(f(contact = 0))[FollowUpField.CONTACT])
    @Test fun blankTitle() = assertEquals(Reason.REQUIRED, FollowUpValidator.validate(f(title = "  "))[FollowUpField.TITLE])
    @Test fun longTitleAndNotes() {
        val e = FollowUpValidator.validate(f(title = "x".repeat(101), notes = "n".repeat(1001)))
        assertEquals(Reason.TOO_LONG, e[FollowUpField.TITLE]); assertEquals(Reason.TOO_LONG, e[FollowUpField.NOTES])
    }
    @Test fun missingDue() = assertEquals(Reason.REQUIRED, FollowUpValidator.validate(f(due = 0))[FollowUpField.DUE])
}
