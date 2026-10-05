package com.yasin.vcardly.domain.backup

import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.vcard.DuplicateDetector

/** Where a merged contact's category goes. */
sealed interface CategoryTarget {
    data object None : CategoryTarget
    data class Existing(val id: Long) : CategoryTarget
    data class Create(val category: BCategory) : CategoryTarget
}

class PlannedContact(
    val contact: BContact,
    val category: CategoryTarget,
    val tagNames: List<String>,
    val followUps: List<BFollowUp>,
)

class MergePlan(val contacts: List<PlannedContact>, val skippedDuplicates: Int)

class ExistingCategory(val id: Long, val name: String, val systemKey: String?)

/**
 * "Merge" restore: add what is missing, never overwrite or delete what is on the device.
 *  - system categories match by key, custom ones by name (case-insensitive), otherwise they are created
 *  - tags match by name (case-insensitive) and are created by the caller
 *  - a contact that already exists (same email / phone / name+company, also within the backup) is skipped together
 *    with its follow-ups, so nothing is duplicated
 */
object RestorePlanner {
    fun planMerge(data: BackupData, existingContacts: List<Contact>, existingCategories: List<ExistingCategory>): MergePlan {
        val backupCategories = data.categories.associateBy { it.id }
        val tagNames = data.tags.associate { it.id to it.name }
        val followUpsByContact = data.followUps.groupBy { it.contactId }

        fun target(categoryId: Long?): CategoryTarget {
            val c = categoryId?.let(backupCategories::get) ?: return CategoryTarget.None
            val match = existingCategories.firstOrNull { e ->
                if (c.systemKey != null) e.systemKey == c.systemKey
                else e.systemKey == null && e.name.trim().equals(c.name.trim(), ignoreCase = true)
            }
            return if (match != null) CategoryTarget.Existing(match.id) else CategoryTarget.Create(c)
        }

        val accepted = mutableListOf<Contact>()
        var skipped = 0
        val planned = mutableListOf<PlannedContact>()
        data.contacts.forEach { b ->
            val probe = Contact(
                fullName = b.fullName, company = b.company, phone = b.phone, phoneAlt = b.phoneAlt, email = b.email, emailAlt = b.emailAlt,
            )
            if (DuplicateDetector.isDuplicate(probe, existingContacts + accepted)) {
                skipped++
            } else {
                accepted += probe
                planned += PlannedContact(
                    contact = b,
                    category = target(b.categoryId),
                    tagNames = b.tagIds.mapNotNull(tagNames::get).distinctBy { it.lowercase() },
                    followUps = followUpsByContact[b.id].orEmpty(),
                )
            }
        }
        return MergePlan(planned, skipped)
    }
}
