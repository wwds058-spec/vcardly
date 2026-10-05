package com.yasin.vcardly.domain.vcard

import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardImporterTest {
    private fun p(name: String, phones: List<String> = emptyList(), emails: List<String> = emptyList(), company: String = "", notes: String = "", website: String = "") =
        ParsedVCard(fullName = name, phones = phones, emails = emails, company = company, notes = notes, website = website)

    @Test fun duplicates_byEmailPhoneOrNameAndCompany() {
        val existing = listOf(Contact(id = 1, fullName = "Asha Rao", company = "Acme", email = "Asha@Acme.com", phone = "+91 98765 43210"))
        val out = VCardImporter.prepare(listOf(
            p("Someone Else", emails = listOf("asha@acme.com")),
            p("Other Name", phones = listOf("098765 43210")),
            p("  asha   rao ", company = "ACME"),
            p("Brand New", emails = listOf("new@x.com")),
        ), existing)
        assertEquals(listOf(ImportStatus.DUPLICATE, ImportStatus.DUPLICATE, ImportStatus.DUPLICATE, ImportStatus.NEW), out.map { it.status })
    }

    @Test fun sameNameDifferentCompany_isNotDuplicate() {
        val out = VCardImporter.prepare(listOf(p("Asha Rao", company = "Globex")), listOf(Contact(fullName = "Asha Rao", company = "Acme")))
        assertEquals(ImportStatus.NEW, out.single().status)
    }

    @Test fun duplicatesWithinTheSameFile_areCaught() {
        val out = VCardImporter.prepare(listOf(p("A", emails = listOf("a@x.com")), p("B", emails = listOf("a@x.com"))), emptyList())
        assertEquals(listOf(ImportStatus.NEW, ImportStatus.DUPLICATE), out.map { it.status })
    }

    @Test fun invalidValues_moveToNotes_insteadOfBeingLost() {
        val c = VCardImporter.prepare(listOf(
            p("Bob", phones = listOf("+1 555 123 4567 ext 9", "12345678"), emails = listOf("not-an-email"), website = "nowhere", notes = "hello"),
        ), emptyList()).single().contact!!
        assertEquals("", c.phone.takeIf { it.contains("ext") } ?: "")
        assertEquals("12345678", c.phoneAlt)
        assertEquals("", c.email); assertEquals("", c.website)
        assertTrue(c.notes.contains("hello") && c.notes.contains("ext 9") && c.notes.contains("not-an-email") && c.notes.contains("nowhere"))
        assertEquals(ContactSource.IMPORT, c.source)
    }

    @Test fun extraPhonesAndEmails_keptInNotes() {
        val c = VCardImporter.toContact(p("Z", phones = listOf("1111111", "2222222", "3333333"), emails = listOf("a@b.co", "c@d.co", "e@f.co")))!!
        assertTrue(c.notes.contains("3333333") && c.notes.contains("e@f.co"))
    }

    @Test fun nameFallbacks_andUnusable() {
        assertEquals("Acme", VCardImporter.toContact(p("", company = "Acme"))!!.fullName)
        assertEquals("jane", VCardImporter.toContact(p("", emails = listOf("jane@x.org")))!!.fullName)
        assertNull(VCardImporter.toContact(p("", phones = listOf("123456789"))))
        assertEquals(ImportStatus.UNUSABLE, VCardImporter.prepare(listOf(p("")), emptyList()).single().status)
    }

    @Test fun veryLongName_isTruncatedToFormLimit() {
        assertEquals(100, VCardImporter.toContact(p("n".repeat(500)))!!.fullName.length)
    }

    @Test fun importedContact_passesFormValidation() {
        val c = VCardImporter.toContact(p("Cara", phones = listOf("garbage"), emails = listOf("x"), website = "%%"))!!
        assertFalse(com.yasin.vcardly.domain.usecase.ContactValidator.validate(c).isNotEmpty())
    }

    @Test fun overlongTitleCompanyAndAddress_keptInNotes() {
        val longTitle = "t".repeat(150)
        val c = VCardImporter.toContact(ParsedVCard(fullName = "Kim", jobTitle = longTitle, company = "c".repeat(120), address = "a".repeat(400)))!!
        assertEquals(100, c.jobTitle.length)
        assertTrue(c.notes.contains(longTitle) && c.notes.contains("c".repeat(120)) && c.notes.contains("a".repeat(400)))
    }
}
