package com.yasin.vcardly.domain.report

import com.yasin.vcardly.core.export.PdfBlock
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPdfContentTest {
    private val strings = ReportStrings(
        "Title", "Generated", "Range", "Summary", "Total", "Favs", "Added", "PerMonth", "FollowUps", "Due", "Done", "Overdue",
        "Rate", "ByType", "ByCat", "Uncat", "BySource", "Tags", "Directory", listOf("Name", "Company", "Phone", "Email"), "None",
    )
    private val now = Instant.parse("2026-06-15T12:00:00Z")

    private fun doc(contacts: List<ContactDetails>, followUps: List<FollowUp> = emptyList()): com.yasin.vcardly.core.export.PdfDoc {
        val report = ReportBuilder.build(contacts, followUps, ReportRange.LAST_3_MONTHS, now, ZoneOffset.UTC)
        return ReportPdfContent.build(
            report, strings, { it?.name.orEmpty() }, { it.name }, { it.name }, { it.toString() },
            contacts.map { listOf(it.contact.fullName, it.contact.company, it.contact.phone, it.contact.email) },
            NumberFormat.getIntegerInstance(Locale.ROOT),
        )
    }

    @Test fun emptyReport_hasAllSections_withPlaceholders_andNoDirectory() {
        val d = doc(emptyList())
        val headings = d.blocks.filterIsInstance<PdfBlock.Heading>().map { it.text }
        assertEquals(listOf("Summary", "PerMonth", "FollowUps", "ByCat", "BySource", "Tags"), headings)
        assertTrue(d.blocks.none { it is PdfBlock.TableBlock })
        assertEquals(3, (d.blocks.first { it is PdfBlock.Bars } as PdfBlock.Bars).rows.size) // 3 zero-filled months
        val kv = d.blocks.filterIsInstance<PdfBlock.KeyValues>().last().rows
        assertEquals("None", kv.last().second)                                              // no completion rate when nothing was due
    }

    @Test fun populatedReport_hasNormalisedBars_andDirectory() {
        val cat = Category(1, "Client", 0)
        val contacts = listOf(
            ContactDetails(Contact(id = 1, fullName = "A", categoryId = 1, createdAt = now.toEpochMilli(), source = ContactSource.SCAN), cat, emptyList()),
            ContactDetails(Contact(id = 2, fullName = "B", createdAt = now.toEpochMilli()), null, emptyList()),
            ContactDetails(Contact(id = 3, fullName = "C", categoryId = 1, createdAt = now.toEpochMilli()), cat, emptyList()),
        )
        val fus = listOf(FollowUp(contactId = 1, title = "x", dueAt = now.toEpochMilli() - 1000, status = FollowUpStatus.COMPLETED, type = FollowUpType.CALL))
        val d = doc(contacts, fus)
        val bars = d.blocks.filterIsInstance<PdfBlock.Bars>()
        bars.forEach { b -> assertEquals(1f, b.rows.maxOf { it.fraction }, 0.0001f) }       // largest bar is always 100%
        val catBars = bars.first { b -> b.rows.any { it.label == "Client" } }
        assertEquals(listOf("Client", "Uncat"), catBars.rows.map { it.label })              // uncategorised gets the localized label
        assertEquals("100%", d.blocks.filterIsInstance<PdfBlock.KeyValues>().last().rows.last().second)
        val table = d.blocks.filterIsInstance<PdfBlock.TableBlock>().single()
        assertEquals(3, table.rows.size); assertEquals(table.header.size, table.weights.size)
    }
}
