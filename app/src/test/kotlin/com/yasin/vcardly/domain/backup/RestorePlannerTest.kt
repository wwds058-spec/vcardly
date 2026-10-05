package com.yasin.vcardly.domain.backup

import com.yasin.vcardly.domain.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePlannerTest {
    private val data = BackupData(
        categories = listOf(BCategory(1, "", 0, "client"), BCategory(7, "Investors", 5), BCategory(8, "Mentors", 6)),
        tags = listOf(BTag(1, "VIP"), BTag(2, "vip"), BTag(3, "Lead")),
        contacts = listOf(
            BContact(1, "Asha Rao", company = "Acme", email = "asha@acme.com", categoryId = 1, tagIds = listOf(1, 2, 3)),
            BContact(2, "Chen", categoryId = 7, phone = "+1 555 123 4567"),
            BContact(3, "Dina", categoryId = 8),
            BContact(4, "Chen Duplicate In File", phone = "0015551234567"),
            BContact(5, "Existing Person", email = "EXISTING@x.com"),
        ),
        followUps = listOf(BFollowUp(1, 1, "a"), BFollowUp(2, 1, "b"), BFollowUp(3, 5, "orphaned by skip")),
    )
    private val existingContacts = listOf(Contact(id = 9, fullName = "Someone", email = "existing@x.com"))
    private val existingCategories = listOf(ExistingCategory(10, "", "client"), ExistingCategory(11, "investors", null))

    @Test fun mergeMatchesCategoriesAndCreatesMissingOnes() {
        val plan = RestorePlanner.planMerge(data, existingContacts, existingCategories)
        val byName = plan.contacts.associateBy { it.contact.fullName }
        assertEquals(CategoryTarget.Existing(10), byName.getValue("Asha Rao").category)      // system key match
        assertEquals(CategoryTarget.Existing(11), byName.getValue("Chen").category)           // custom name, case-insensitive
        assertTrue(byName.getValue("Dina").category is CategoryTarget.Create)
        assertEquals("Mentors", (byName.getValue("Dina").category as CategoryTarget.Create).category.name)
    }

    @Test fun duplicatesAreSkipped_withTheirFollowUps_andTagsDeduped() {
        val plan = RestorePlanner.planMerge(data, existingContacts, existingCategories)
        assertEquals(listOf("Asha Rao", "Chen", "Dina"), plan.contacts.map { it.contact.fullName })
        assertEquals(2, plan.skippedDuplicates)                                                // in-file phone duplicate + existing email
        assertEquals(listOf("VIP", "Lead"), plan.contacts.first().tagNames)                    // "vip" == "VIP"
        assertEquals(listOf(1L, 2L), plan.contacts.first().followUps.map { it.id })
        assertTrue(plan.contacts.none { c -> c.followUps.any { it.id == 3L } })
    }

    @Test fun emptyBackup_plansNothing() {
        val plan = RestorePlanner.planMerge(BackupData(), existingContacts, existingCategories)
        assertTrue(plan.contacts.isEmpty()); assertEquals(0, plan.skippedDuplicates)
    }
}
