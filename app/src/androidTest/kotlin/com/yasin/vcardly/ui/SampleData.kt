package com.yasin.vcardly.ui

import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.domain.model.MyCard
import com.yasin.vcardly.domain.model.SystemCategory
import com.yasin.vcardly.domain.model.Tag
import com.yasin.vcardly.domain.usecase.CategoryBreakdownItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Fictional sample people and companies for UI tests and screenshots (no real data). */
object SampleData {
    private val zone: ZoneId = ZoneId.systemDefault()
    private fun at(daysFromToday: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.now(zone).plusDays(daysFromToday).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    val now: Long = System.currentTimeMillis()

    val client = Category(1, "", SystemCategory.CLIENT.colorArgb, SystemCategory.CLIENT)
    val partner = Category(2, "", SystemCategory.PARTNER.colorArgb, SystemCategory.PARTNER)
    val vendor = Category(3, "", SystemCategory.VENDOR.colorArgb, SystemCategory.VENDOR)
    val friend = Category(4, "", SystemCategory.FRIEND.colorArgb, SystemCategory.FRIEND)
    val categories = listOf(client, partner, vendor, friend)

    val vip = Tag(1, "VIP")
    val potential = Tag(2, "Potential client")
    val tags = listOf(vip, potential)

    private fun contact(id: Long, name: String, title: String, company: String, phone: String, email: String, cat: Category?, fav: Boolean, daysAgo: Long, tags: List<Tag> = emptyList(), website: String = "", address: String = "", notes: String = "") =
        ContactDetails(
            Contact(
                id = id, fullName = name, jobTitle = title, company = company, phone = phone, email = email, website = website,
                address = address, notes = notes, categoryId = cat?.id, isFavorite = fav, source = if (id % 2L == 0L) ContactSource.SCAN else ContactSource.MANUAL,
                createdAt = now - daysAgo * 86_400_000L, updatedAt = now - daysAgo * 86_400_000L + 3_600_000L,
            ),
            cat, tags,
        )

    val rajesh = contact(
        1, "Rajesh Kumar", "Business Development", "ABC Technologies", "+91 98765 43210", "rajesh@abctech.example", client, true, 2, listOf(vip),
        website = "www.abctech.example", address = "Banjara Hills, Hyderabad, Telangana",
        notes = "Met at the Hyderabad trade expo. Interested in a yearly supply contract; send pricing for 500 units.",
    )
    val priya = contact(2, "Priya Sharma", "Marketing Manager", "Global Solutions", "+91 91234 56780", "priya@globalsol.example", partner, true, 5, listOf(potential))
    val mohammed = contact(3, "Mohammed Ali", "Owner", "XYZ Trading", "+971 50 123 4567", "ali@xyztrading.example", vendor, false, 9)
    val sneha = contact(4, "Sneha Reddy", "Sales Manager", "Innovate Systems", "+91 99887 76655", "", client, true, 14)
    val anil = contact(5, "Anil Verma", "Director", "Verma Enterprises", "", "anil@verma.example", null, false, 20)
    val contacts = listOf(rajesh, priya, mohammed, sneha, anil)

    val followUps = listOf(
        FollowUpWithContact(FollowUp(1, 1, FollowUpType.CALL, title = "Discuss wholesale proposal", dueAt = at(0, 16, 30), createdAt = now - 86_400_000L), "Rajesh Kumar", "ABC Technologies"),
        FollowUpWithContact(FollowUp(2, 2, FollowUpType.QUOTATION, title = "Send quotation", dueAt = at(0, 18), createdAt = now - 2 * 86_400_000L), "Priya Sharma", "Global Solutions"),
        FollowUpWithContact(FollowUp(3, 3, FollowUpType.MEETING, FollowUpStatus.RESCHEDULED, "Follow up on meeting", dueAt = at(1, 11), createdAt = now - 3 * 86_400_000L), "Mohammed Ali", "XYZ Trading"),
        FollowUpWithContact(FollowUp(4, 4, FollowUpType.WHATSAPP, title = "Project discussion", dueAt = at(5, 10), createdAt = now), "Sneha Reddy", "Innovate Systems"),
    )
    val overdue = FollowUpWithContact(FollowUp(5, 5, FollowUpType.PAYMENT, title = "Payment reminder for invoice 1042", dueAt = at(-2, 12), createdAt = now - 5 * 86_400_000L), "Anil Verma", "Verma Enterprises")

    val counts = FollowUpCounts(today = 2, upcoming = 6, overdue = 3, completed = 12)

    val breakdown = listOf(
        CategoryBreakdownItem(client, 54, 0.42f),
        CategoryBreakdownItem(partner, 36, 0.28f),
        CategoryBreakdownItem(vendor, 23, 0.18f),
        CategoryBreakdownItem(null, 15, 0.12f),
    )

    val myCard = MyCard(
        fullName = "Yasin Khan", jobTitle = "Founder & Developer", company = "VCardly", phone = "+91 90000 12345",
        email = "hello@vcardly.example", website = "vcardly.example", address = "Hyderabad, India",
    )

    val riyaFollowUps = listOf(
        FollowUp(10, 1, FollowUpType.CALL, title = "Discuss wholesale proposal", dueAt = at(0, 16, 30), createdAt = now - 86_400_000L),
        FollowUp(11, 1, FollowUpType.EMAIL, FollowUpStatus.COMPLETED, "Send company profile", dueAt = at(-3, 10), completedAt = now - 2 * 86_400_000L, createdAt = now - 4 * 86_400_000L),
    )
}
