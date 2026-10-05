package com.yasin.vcardly.domain.vcard

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {
    private val full = VCardData(
        fullName = "Asha Rao", jobTitle = "Senior Engineer, R&D", company = "Acme; Inc",
        phones = listOf(VCardPhone("+91 98765 43210", "CELL"), VCardPhone("040 2345 6789", "WORK")),
        emails = listOf("asha@acme.com", "asha.rao@home.org"), website = "https://acme.com/a,b",
        address = "Plot 12, Road 5\nBanjara Hills; Hyderabad", notes = "Met at Expo; likes tea, no sugar",
        categories = listOf("Client", "VIP, Gold"),
    )

    @Test fun roundTrip_preservesEveryField_includingEscapedCharacters() {
        val text = VCardWriter.write(full, Instant.parse("2026-01-05T10:00:00Z"))
        assertTrue(text.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(text.contains("REV:20260105T100000Z"))
        val p = VCardParser.parse(text).single()
        assertEquals("Asha Rao", p.fullName)
        assertEquals("Senior Engineer, R&D", p.jobTitle)
        assertEquals("Acme; Inc", p.company)
        assertEquals(listOf("+91 98765 43210", "040 2345 6789"), p.phones)
        assertEquals(listOf("asha@acme.com", "asha.rao@home.org"), p.emails)
        assertEquals("https://acme.com/a,b", p.website)
        assertEquals("Plot 12, Road 5\nBanjara Hills; Hyderabad", p.address)
        assertEquals("Met at Expo; likes tea, no sugar", p.notes)
        assertEquals(listOf("Client", "VIP, Gold"), p.categories)
    }

    @Test fun addressIsReadAsOneLine() {
        val p = VCardParser.parse(VCardWriter.write(VCardData("A", address = "Plot 12, Road 5")))
        assertEquals("Plot 12, Road 5", p.single().address)
    }

    @Test fun longLinesAreFoldedAtOrBelow75Octets_withoutSplittingCharacters() {
        val telugu = "హరి".repeat(40) // multi-byte
        val text = VCardWriter.write(VCardData(fullName = telugu, notes = "x".repeat(300)))
        text.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        val p = VCardParser.parse(text).single()
        assertEquals(telugu, p.fullName)
        assertEquals("x".repeat(300), p.notes)
    }

    @Test fun nonLatinNamesSurvive() {
        val p = VCardParser.parse(VCardWriter.write(VCardData(fullName = "रमेश कुमार", company = "اردو کمپنی"))).single()
        assertEquals("रमेश कुमार", p.fullName); assertEquals("اردو کمپنی", p.company)
    }

    @Test fun multipleCardsInOneFile() {
        val text = VCardWriter.write(listOf(VCardData("One"), VCardData("Two"), VCardData("Three")))
        assertEquals(listOf("One", "Two", "Three"), VCardParser.parse(text).map { it.fullName })
    }

    @Test fun vCard21_bareTypes_quotedPrintable_groups_andFaxExcluded() {
        val text = "BEGIN:VCARD\nVERSION:2.1\nN:Müller;Jörg;;Dr.;\nFN;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:J=C3=B6rg M=C3=BCller\n" +
            "item1.TEL;WORK;FAX:111 222 333\nTEL;VOICE:020 7946 0958\nTEL;CELL;VOICE:07911 123456\n" +
            "EMAIL;INTERNET:jorg@example.de\nNOTE;ENCODING=QUOTED-PRINTABLE:line one=0Aline =\ntwo\nEND:VCARD\n"
        val p = VCardParser.parse(text).single()
        assertEquals("Jörg Müller", p.fullName)
        assertEquals(listOf("07911 123456", "020 7946 0958"), p.phones) // mobile first, fax dropped
        assertEquals("line one\nline two", p.notes)
    }

    @Test fun nameFallsBackToStructuredN() {
        val p = VCardParser.parse("BEGIN:VCARD\nVERSION:3.0\nN:Rao;Asha;K;;\nEND:VCARD").single()
        assertEquals("Asha K Rao", p.fullName)
    }

    @Test fun garbageNeverThrows() {
        assertTrue(VCardParser.parse("").isEmpty())
        assertTrue(VCardParser.parse("not a vcard at all\n:::\nBEGIN:VCARD\nEND:VCARD").isEmpty())
        assertTrue(VCardParser.parse("BEGIN:VCARD\nFN:Unterminated").isEmpty())
        VCardParser.parse("BEGIN:VCARD\nTEL;ENCODING=QUOTED-PRINTABLE:=ZZ=\nFN:X\nEND:VCARD") // must not throw
    }

    @Test fun bomAndMixedLineEndings() {
        val p = VCardParser.parse("﻿BEGIN:VCARD\r\nFN:Bom Test\rEND:VCARD\r\n").single()
        assertEquals("Bom Test", p.fullName)
    }
}
