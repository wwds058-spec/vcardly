package com.yasin.vcardly.domain.report

import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.model.Tag
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-06-15T12:00:00Z")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private val client = Category(id = 1, name = "", colorArgb = 0, sortOrder = 0)
    private val vip = Tag(1, "VIP"); private val lead = Tag(2, "lead")

    private fun contact(id: Long, created: String, fav: Boolean = false, cat: Category? = null, tags: List<Tag> = emptyList(), src: ContactSource = ContactSource.MANUAL) =
        ContactDetails(Contact(id = id, fullName = "C$id", isFavorite = fav, categoryId = cat?.id, source = src, createdAt = ms(created)), cat, tags)

    private fun fu(id: Long, due: String, status: FollowUpStatus = FollowUpStatus.PENDING, type: FollowUpType = FollowUpType.CALL) =
        FollowUp(id = id, contactId = 1, title = "t", dueAt = ms(due), status = status, type = type)

    @Test fun emptyData_givesZeroFilledMonths_andNoDivisionByZero() {
        val r = ReportBuilder.build(emptyList(), emptyList(), ReportRange.LAST_12_MONTHS, now, zone)
        assertEquals(12, r.monthly.size)
        assertEquals(YearMonth.of(2025, 7), r.monthly.first().month); assertEquals(YearMonth.of(2026, 6), r.monthly.last().month)
        assertTrue(r.monthly.all { it.count == 0 })
        assertEquals(0, r.totalContacts); assertNull(r.followUps.completionRate)
        assertTrue(r.byCategory.isEmpty() && r.topTags.isEmpty() && r.bySource.isEmpty())
    }

    @Test fun addedInRange_andMonthlyBuckets() {
        val contacts = listOf(
            contact(1, "2026-06-01T00:00:00Z"), contact(2, "2026-06-30T23:59:59Z"),
            contact(3, "2026-04-10T08:00:00Z"), contact(4, "2026-03-31T23:59:59Z"), contact(5, "2024-01-01T00:00:00Z"),
        )
        val three = ReportBuilder.build(contacts, emptyList(), ReportRange.LAST_3_MONTHS, now, zone)
        assertEquals(listOf(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)), three.monthly.map { it.month })
        assertEquals(listOf(1, 0, 2), three.monthly.map { it.count })
        assertEquals(3, three.addedInRange)                     // March and 2024 are outside
        assertEquals(5, three.totalContacts)

        val all = ReportBuilder.build(contacts, emptyList(), ReportRange.ALL_TIME, now, zone)
        assertEquals(YearMonth.of(2024, 1), all.monthly.first().month)
        assertEquals(5, all.addedInRange)
        assertEquals(5, all.monthly.sumOf { it.count })
    }

    @Test fun allTime_isCappedTo36Months() {
        val r = ReportBuilder.build(listOf(contact(1, "2015-01-01T00:00:00Z")), emptyList(), ReportRange.ALL_TIME, now, zone)
        assertEquals(36, r.monthly.size)
        assertEquals(1, r.addedInRange) // still counted even though outside the charted window
    }

    @Test fun monthBoundariesFollowTheDeviceZone() {
        val tokyo = ZoneOffset.ofHours(9)
        // 2026-05-31T20:00Z is already June 1st in Tokyo
        val r = ReportBuilder.build(listOf(contact(1, "2026-05-31T20:00:00Z")), emptyList(), ReportRange.LAST_3_MONTHS, now, tokyo)
        assertEquals(1, r.monthly.first { it.month == YearMonth.of(2026, 6) }.count)
    }

    @Test fun libraryFigures_categoriesTagsSourcesFavorites() {
        val contacts = listOf(
            contact(1, "2026-06-01T00:00:00Z", fav = true, cat = client, tags = listOf(vip, lead), src = ContactSource.SCAN),
            contact(2, "2026-06-02T00:00:00Z", cat = client, tags = listOf(vip), src = ContactSource.SCAN),
            contact(3, "2026-06-03T00:00:00Z", src = ContactSource.IMPORT),
        )
        val r = ReportBuilder.build(contacts, emptyList(), ReportRange.LAST_12_MONTHS, now, zone)
        assertEquals(1, r.favorites)
        assertEquals(listOf("VIP" to 2, "lead" to 1), r.topTags)
        assertEquals(listOf(ContactSource.SCAN to 2, ContactSource.IMPORT to 1), r.bySource)
        assertEquals(listOf(2, 1), r.byCategory.map { it.count })
        assertNull(r.byCategory.last().category) // uncategorised
    }

    @Test fun followUpActivity_countsOnlyWhatIsAlreadyDue() {
        val list = listOf(
            fu(1, "2026-06-10T09:00:00Z", FollowUpStatus.COMPLETED, FollowUpType.CALL),
            fu(2, "2026-06-11T09:00:00Z", FollowUpStatus.PENDING, FollowUpType.EMAIL),     // overdue
            fu(3, "2026-06-20T09:00:00Z", FollowUpStatus.PENDING),                          // future: not counted
            fu(4, "2026-06-12T09:00:00Z", FollowUpStatus.COMPLETED, FollowUpType.CALL),
            fu(5, "2025-01-01T09:00:00Z", FollowUpStatus.COMPLETED),                        // before 3-month range
        )
        val three = ReportBuilder.build(emptyList(), list, ReportRange.LAST_3_MONTHS, now, zone).followUps
        assertEquals(3, three.dueSoFar); assertEquals(2, three.completed); assertEquals(1, three.overdue)
        assertEquals(2f / 3f, three.completionRate!!, 0.0001f)
        assertEquals(listOf(FollowUpType.CALL to 2, FollowUpType.EMAIL to 1), three.byType)
        assertEquals(4, ReportBuilder.build(emptyList(), list, ReportRange.ALL_TIME, now, zone).followUps.dueSoFar)
    }
}
