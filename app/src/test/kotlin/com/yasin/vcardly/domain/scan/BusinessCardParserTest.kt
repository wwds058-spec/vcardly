package com.yasin.vcardly.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessCardParserTest {
    private fun lines(vararg specs: Triple<String, Int, Int>) = specs.map { OcrLine(it.first, it.second, it.third) }
    private fun l(text: String, top: Int, h: Int = 20) = Triple(text, top, h)

    @Test fun indianCard_withLabels_andFax() {
        val p = BusinessCardParser.parse(lines(
            l("ACME TECHNOLOGIES PVT LTD", 10, 40), l("Asha Rao", 60, 36), l("Senior Software Engineer", 100),
            l("M: +91 98765 43210", 140), l("T: 040 2345 6789  F: 040 2345 6780", 170),
            l("asha.rao@acme.com", 200), l("www.acme.com", 230),
            l("Plot 12, Road No 5, Banjara Hills", 260), l("Hyderabad - 500034", 290),
        ))
        assertEquals("Asha Rao", p.fullName)
        assertEquals("Senior Software Engineer", p.jobTitle)
        assertEquals("ACME TECHNOLOGIES PVT LTD", p.company)
        assertEquals("+91 98765 43210", p.phone)
        assertEquals("040 2345 6789", p.phoneAlt)
        assertEquals("asha.rao@acme.com", p.email)
        assertEquals("www.acme.com", p.website)
        assertEquals("Plot 12, Road No 5, Banjara Hills, Hyderabad - 500034", p.address)
        // The fax number is not a contact phone, but it is surfaced rather than silently dropped.
        assertTrue(p.unmatched.any { it.contains("2345 6780") })
    }

    @Test fun westernCard_withoutLabels() {
        val p = BusinessCardParser.parse(lines(
            l("Jane Doe", 20, 44), l("Marketing Director", 70), l("Globex Corporation", 100, 28),
            l("jane@globex.io", 140), l("+1 (555) 123-4567", 170), l("globex.io", 200),
            l("123 Main Street, Springfield, IL 62704", 230),
        ))
        assertEquals("Jane Doe", p.fullName)
        assertEquals("Marketing Director", p.jobTitle)
        assertEquals("Globex Corporation", p.company)
        assertEquals("+1 (555) 123-4567", p.phone)
        assertEquals("jane@globex.io", p.email)
        assertEquals("globex.io", p.website)
        assertEquals("123 Main Street, Springfield, IL 62704", p.address)
        assertTrue(p.unmatched.isEmpty())
    }

    @Test fun companyInferredFromEmailDomain() {
        val p = BusinessCardParser.parse(lines(
            l("BrightPath", 10, 30), l("Rahul Verma", 50, 40), l("Founder", 90), l("rahul@brightpath.in", 130), l("+91 99999 12345", 160),
        ))
        assertEquals("BrightPath", p.company)
        assertEquals("Rahul Verma", p.fullName)
        assertEquals("Founder", p.jobTitle)
    }

    @Test fun nameUsesEmailAsTieBreaker() {
        val p = BusinessCardParser.parse(lines(
            l("Quality Assurance Team", 10, 30), l("JOHN SMITH", 60, 30), l("john.smith@example.org", 100),
        ))
        assertEquals("JOHN SMITH", p.fullName)
    }

    @Test fun abbreviationsAreNotWebsites() {
        val p = BusinessCardParser.parse(lines(l("Dr.Rao", 10, 30), l("Acme Pvt.Ltd", 50), l("rao@acme.com", 90)))
        assertEquals("", p.website)
        assertEquals("Acme Pvt.Ltd", p.company)
    }

    @Test fun twoEmailsAndPhones_keepOrder() {
        val p = BusinessCardParser.parse(lines(
            l("a@x.com", 10), l("b@y.com", 20), l("+44 20 7946 0958", 30), l("Mob: 07911 123456", 40),
        ))
        assertEquals("a@x.com", p.email); assertEquals("b@y.com", p.emailAlt)
        assertEquals("07911 123456", p.phone) // labelled mobile wins
        assertEquals("+44 20 7946 0958", p.phoneAlt)
    }

    @Test fun emptyAndGarbage_doNotCrash() {
        assertEquals(ParsedCard(), BusinessCardParser.parse(emptyList()))
        val p = BusinessCardParser.parse(lines(l("   ", 0), l("•••", 10), l("---", 20)))
        assertEquals("", p.fullName)
        assertTrue(p.unmatched.isEmpty())
    }
}
