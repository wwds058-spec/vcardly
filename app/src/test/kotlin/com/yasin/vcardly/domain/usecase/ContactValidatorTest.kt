package com.yasin.vcardly.domain.usecase

import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactValidatorTest {
    private fun c(
        name: String = "Asha", phone: String = "", email: String = "", website: String = "",
        company: String = "", notes: String = "",
    ) = Contact(fullName = name, phone = phone, email = email, website = website, company = company, notes = notes)

    @Test fun minimalContact_isValid() = assertTrue(ContactValidator.validate(c()).isEmpty())

    @Test fun blankName_isRequired() {
        assertEquals(Reason.REQUIRED, ContactValidator.validate(c(name = "   "))[ContactField.FULL_NAME])
    }

    @Test fun longName_tooLong() {
        assertEquals(Reason.TOO_LONG, ContactValidator.validate(c(name = "x".repeat(101)))[ContactField.FULL_NAME])
    }

    @Test fun emails() {
        listOf("a@b.co", "first.last+tag@sub.example.com").forEach {
            assertTrue(it, ContactValidator.validate(c(email = it)).isEmpty())
        }
        listOf("plain", "a@b", "a@b.c", "a b@c.com", "@c.com", "a@@c.com").forEach {
            assertEquals(it, Reason.INVALID_FORMAT, ContactValidator.validate(c(email = it))[ContactField.EMAIL])
        }
    }

    @Test fun phones() {
        listOf("+91 98765 43210", "(040) 1234-5678", "12345").forEach {
            assertTrue(it, ContactValidator.validate(c(phone = it)).isEmpty())
        }
        listOf("1234", "abcde12345", "+1 (555) 123-4567 ext 9", "1".repeat(16)).forEach {
            assertEquals(it, Reason.INVALID_FORMAT, ContactValidator.validate(c(phone = it))[ContactField.PHONE])
        }
    }

    @Test fun websites() {
        listOf("acme.com", "https://www.acme.co.uk/path?x=1", "http://acme.com:8080", "bücher.de").forEach {
            assertTrue(it, ContactValidator.validate(c(website = it)).isEmpty())
        }
        listOf("acme", "http://", "ftp://acme.com", "acme .com").forEach {
            assertEquals(it, Reason.INVALID_FORMAT, ContactValidator.validate(c(website = it))[ContactField.WEBSITE])
        }
    }

    @Test fun multipleErrors_reportedTogether() {
        val errors = ContactValidator.validate(c(name = "", email = "bad", notes = "n".repeat(2001)))
        assertEquals(
            mapOf(ContactField.FULL_NAME to Reason.REQUIRED, ContactField.EMAIL to Reason.INVALID_FORMAT, ContactField.NOTES to Reason.TOO_LONG),
            errors,
        )
    }
}
