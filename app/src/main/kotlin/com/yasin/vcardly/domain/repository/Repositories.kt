package com.yasin.vcardly.domain.repository

import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.ContactStats
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.domain.model.Tag
import com.yasin.vcardly.domain.model.TagWithCount
import com.yasin.vcardly.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeContacts(filter: ContactFilter): Flow<List<ContactDetails>>
    fun observeContact(id: Long): Flow<ContactDetails?>
    suspend fun getContact(id: Long): ContactDetails?

    /** Inserts when [Contact.id] is 0, otherwise updates. Replaces the contact's tags atomically. Returns the id. */
    suspend fun save(contact: Contact, tagIds: Set<Long>): Long
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun delete(id: Long)

    /** [addedSince] is an epoch-millis lower bound for the "recently added" figure. */
    fun observeStats(addedSince: Long): Flow<ContactStats>
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun save(category: Category): Long

    /** Contacts in the deleted category become uncategorised. System categories cannot be deleted. */
    suspend fun delete(id: Long): Boolean
}

interface TagRepository {
    fun observeAll(): Flow<List<TagWithCount>>
    /** Case-insensitive; returns the existing tag when the name already exists. */
    suspend fun findOrCreate(name: String): Tag?
    suspend fun rename(id: Long, name: String): Boolean
    suspend fun delete(id: Long)
}

interface FollowUpRepository {
    fun observe(bucket: FollowUpBucket): Flow<List<FollowUpWithContact>>
    fun observeForContact(contactId: Long): Flow<List<FollowUp>>
    fun observeCounts(): Flow<FollowUpCounts>
    suspend fun get(id: Long): FollowUp?
    suspend fun getWithContact(id: Long): FollowUpWithContact?
    suspend fun markNotified(id: Long, at: Long)
    suspend fun save(followUp: FollowUp): Long
    suspend fun markCompleted(id: Long)
    suspend fun reopen(id: Long)
    suspend fun delete(id: Long)

    /** Pending follow-ups with reminders on; used to re-arm alarms after reboot/time change. */
    suspend fun getReminderCandidates(): List<FollowUp>
}

interface PreferencesRepository {
    val themeMode: Flow<ThemeMode>
    val onboardingCompleted: Flow<Boolean>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingCompleted(completed: Boolean)
}
