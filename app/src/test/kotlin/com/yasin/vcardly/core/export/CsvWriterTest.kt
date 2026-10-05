package com.yasin.vcardly.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvWriterTest {
    @Test fun quotesCommasQuotesAndNewlines() {
        assertEquals("plain", CsvWriter.cell("plain"))
        assertEquals("\"a,b\"", CsvWriter.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.cell("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvWriter.cell("line1\nline2"))
        assertEquals("\" padded \"", CsvWriter.cell(" padded "))
    }

    @Test fun formulaInjectionIsNeutralised_butPhoneNumbersAreNot() {
        listOf("=1+1", "=HYPERLINK(\"http://evil\",\"x\")", "+cmd|' /C calc'!A0", "-2+3+cmd", "@SUM(A1)", "\t=1", "-A1").forEach {
            assertTrue(it, CsvWriter.neutralise(it).startsWith("'"))
        }
        listOf("+91 98765 43210", "+1 (555) 123-4567", "-5", "040-2345-6789", "(040) 2345", "Normal text", "").forEach {
            assertEquals(it, CsvWriter.neutralise(it))
        }
    }

    @Test fun tableUsesCrlf_andHeaderFirst() {
        val csv = CsvWriter.write(Table("t", listOf("A", "B"), listOf(listOf("1", "x,y"), listOf("", "=bad"))))
        assertEquals("A,B\r\n1,\"x,y\"\r\n,'=bad\r\n", csv)
    }
}
