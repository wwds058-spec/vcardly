package com.yasin.vcardly.domain.vcard

import com.yasin.vcardly.domain.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardTest {
    private val card = ShareCard.of(
        Contact(fullName = "Asha Rao", jobTitle = "CTO", company = "Acme", phone = "+91 98765 43210", email = "a@acme.com",
            address = "Hyderabad", notes = "private note about Asha"),
    )

    @Test fun onlyFieldsWithValuesAreAvailable() {
        assertEquals(setOf(ShareField.NAME, ShareField.JOB_TITLE, ShareField.COMPANY, ShareField.PHONE, ShareField.EMAIL, ShareField.ADDRESS), card.available.toSet())
    }

    @Test fun defaultSelection_excludesAddressAndNeverMissingFields() {
        assertEquals(setOf(ShareField.NAME, ShareField.JOB_TITLE, ShareField.COMPANY, ShareField.PHONE, ShareField.EMAIL), card.defaultSelection)
    }

    @Test fun deselectedFieldsAreAbsentFromTheVCard() {
        val text = VCardWriter.write(card.toVCard(setOf(ShareField.EMAIL)))
        assertTrue(text.contains("FN:Asha Rao")) // name is always present
        assertTrue(text.contains("a@acme.com"))
        assertFalse(text.contains("98765")); assertFalse(text.contains("Acme")); assertFalse(text.contains("Hyderabad"))
    }

    @Test fun privateNotesCanNeverBeShared() {
        val text = VCardWriter.write(card.toVCard(ShareField.entries.toSet()))
        assertFalse(text.contains("private note"))
        assertFalse(text.contains("NOTE"))
    }
}
