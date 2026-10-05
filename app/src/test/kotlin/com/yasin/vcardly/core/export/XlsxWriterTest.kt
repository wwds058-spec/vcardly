package com.yasin.vcardly.core.export

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxWriterTest {
    private val contacts = Table(
        "Contacts",
        listOf("Name", "Phone", "Notes"),
        listOf(
            listOf("Asha Rao", "+91 98765 43210", "Met at Expo & liked <tea>"),
            listOf("హరి రమ", "", "line1\nline2"),
            listOf("=1+1", "-", "bad\u0001control\u0000chars"),
        ),
    )
    private val followUps = Table("Follow-ups", listOf("Title"), listOf(listOf("Call Asha")))

    private fun entries(bytes: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            generateSequence { z.nextEntry }.forEach { out[it.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        return out
    }

    @Test fun everyPartIsWellFormedXml_andExpectedPartsExist() {
        val parts = entries(XlsxWriter.toBytes(listOf(contacts, followUps)))
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"),
            parts.keys,
        )
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        parts.values.forEach { builder.parse(ByteArrayInputStream(it.toByteArray(Charsets.UTF_8))) } // throws if malformed
    }

    @Test fun illegalControlCharactersAreRemoved_andFormulasStayText() {
        val sheet = entries(XlsxWriter.toBytes(listOf(contacts)))["xl/worksheets/sheet1.xml"]!!
        assertFalse(sheet.contains("\u0001")); assertFalse(sheet.contains("\u0000"))
        assertTrue(sheet.contains("&amp;") && sheet.contains("&lt;tea&gt;"))
        assertFalse(sheet.contains("<f>")) // never a formula
        assertTrue(sheet.contains("t=\"inlineStr\""))
    }

    @Test fun columnLettersAndSheetNames() {
        assertEquals(listOf("A", "Z", "AA", "AB", "AZ", "BA"), listOf(0, 25, 26, 27, 51, 52).map(XlsxWriter::column))
        assertEquals(listOf("A B", "Sheet2", "Dup", "dup (2)"), XlsxWriter.uniqueSheetNames(listOf("A/B", "  ", "Dup", "dup")))
        assertEquals(31, XlsxWriter.uniqueSheetNames(listOf("x".repeat(80))).single().length)
    }

    /** Writes a sample file so an independent reader (openpyxl) can open it in the check script. */
    @Test fun writesSampleFileForExternalValidation() {
        val file = File("build/sample-export.xlsx").also { it.parentFile.mkdirs() }
        file.outputStream().use { XlsxWriter.write(listOf(contacts, followUps), it) }
        assertTrue(file.length() > 500)
    }
}
