package com.yasin.vcardly.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.data.repository.ContactRepositoryImpl
import com.yasin.vcardly.data.repository.TagRepositoryImpl
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactFilter
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactRepositoryTest {
    private lateinit var db: VCardlyDatabase
    private var now = 1_000L
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
    private lateinit var contacts: ContactRepositoryImpl
    private lateinit var tags: TagRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VCardlyDatabase::class.java).build()
        contacts = ContactRepositoryImpl(db, clock)
        tags = TagRepositoryImpl(db, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun save_insertsThenUpdates_preservingCreatedAt_andReplacesTags() = runBlocking {
        val vip = tags.findOrCreate("VIP")!!
        val lead = tags.findOrCreate("#lead")!!
        val id = contacts.save(Contact(fullName = "  Asha Rao  ", company = "Acme"), setOf(vip.id))

        var saved = contacts.getContact(id)!!
        assertEquals("Asha Rao", saved.contact.fullName)
        assertEquals(1_000L, saved.contact.createdAt)
        assertEquals(listOf("VIP"), saved.tags.map { it.name })

        now = 5_000L
        contacts.save(saved.contact.copy(company = "Globex"), setOf(lead.id))
        saved = contacts.getContact(id)!!
        assertEquals("Globex", saved.contact.company)
        assertEquals(1_000L, saved.contact.createdAt)
        assertEquals(5_000L, saved.contact.updatedAt)
        assertEquals(listOf("lead"), saved.tags.map { it.name })
    }

    @Test
    fun tagFindOrCreate_isCaseInsensitive_andRejectsBlank() = runBlocking {
        val a = tags.findOrCreate("Conference")!!
        assertEquals(a.id, tags.findOrCreate("  conference ")!!.id)
        assertEquals(null, tags.findOrCreate("   #  "))
    }

    @Test
    fun favorite_toggle_andFilter() = runBlocking {
        val id = contacts.save(Contact(fullName = "Bilal"), emptySet())
        contacts.setFavorite(id, true)
        assertEquals(1, contacts.observeContacts(ContactFilter(favoritesOnly = true)).first().size)
        assertNotNull(contacts.observeStats(0).first().takeIf { it.favorites == 1 && it.total == 1 })
    }
}
