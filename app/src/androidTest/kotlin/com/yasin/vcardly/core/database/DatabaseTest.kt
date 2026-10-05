package com.yasin.vcardly.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yasin.vcardly.core.database.entity.CategoryEntity
import com.yasin.vcardly.core.database.entity.ContactEntity
import com.yasin.vcardly.core.database.entity.ContactTagCrossRef
import com.yasin.vcardly.core.database.entity.TagEntity
import com.yasin.vcardly.domain.model.ContactFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: VCardlyDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VCardlyDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addContact(name: String, company: String = "", fav: Boolean = false, categoryId: Long? = null) =
        db.contactDao().insert(
            ContactEntity(fullName = name, company = company, isFavorite = fav, categoryId = categoryId, createdAt = 1, updatedAt = 1),
        )

    private fun query(filter: ContactFilter) = ContactQueryBuilder.build(filter)
        .let { SimpleSQLiteQuery(it.sql, it.args.toTypedArray()) }

    @Test
    fun tagsAreManyToMany_andCascadeOnDelete() = runBlocking {
        val a = addContact("Asha")
        val b = addContact("Bilal")
        val vip = db.tagDao().insert(TagEntity(name = "VIP", createdAt = 1))
        val lead = db.tagDao().insert(TagEntity(name = "Lead", createdAt = 1))
        db.contactDao().insertTagLinks(
            listOf(ContactTagCrossRef(a, vip), ContactTagCrossRef(a, lead), ContactTagCrossRef(b, vip)),
        )
        assertEquals(2, db.contactDao().getDetails(a)!!.tags.size)

        val counts = db.tagDao().observeAllWithCounts().first().associate { it.tag.name to it.contactCount }
        assertEquals(mapOf("Lead" to 1, "VIP" to 2), counts)

        db.contactDao().deleteById(a)
        assertEquals(1, db.tagDao().observeAllWithCounts().first().first { it.tag.name == "VIP" }.contactCount)
    }

    @Test
    fun tagNames_areUniqueCaseInsensitively() = runBlocking {
        db.tagDao().insert(TagEntity(name = "VIP", createdAt = 1))
        assertEquals(-1L, db.tagDao().insert(TagEntity(name = "vip", createdAt = 1)))
        assertEquals("VIP", db.tagDao().findByName("vIp")!!.name)
    }

    @Test
    fun deletingCategory_uncategorisesContacts() = runBlocking {
        val cat = db.categoryDao().insert(CategoryEntity(name = "Investors", colorArgb = 0xFF000000, createdAt = 1))
        val id = addContact("Chen", categoryId = cat)
        db.categoryDao().deleteCustom(cat)
        assertNull(db.contactDao().getById(id)!!.categoryId)
    }

    @Test
    fun filteredQuery_searchesNameCompanyAndTags_andSorts() = runBlocking {
        val a = addContact("Zed Zimmer", company = "Acme", fav = true)
        addContact("Amy Adams", company = "Globex")
        val tag = db.tagDao().insert(TagEntity(name = "Conference", createdAt = 1))
        db.contactDao().insertTagLinks(listOf(ContactTagCrossRef(a, tag)))

        val dao = db.contactDao()
        assertEquals(listOf("Amy Adams", "Zed Zimmer"), dao.observeFiltered(query(ContactFilter())).first().map { it.contact.fullName })
        assertEquals(listOf("Zed Zimmer"), dao.observeFiltered(query(ContactFilter(query = "acme"))).first().map { it.contact.fullName })
        assertEquals(listOf("Zed Zimmer"), dao.observeFiltered(query(ContactFilter(query = "confer"))).first().map { it.contact.fullName })
        assertEquals(listOf("Zed Zimmer"), dao.observeFiltered(query(ContactFilter(favoritesOnly = true))).first().map { it.contact.fullName })
        assertEquals(listOf("Zed Zimmer"), dao.observeFiltered(query(ContactFilter(tagIds = setOf(tag)))).first().map { it.contact.fullName })
        assertEquals(0, dao.observeFiltered(query(ContactFilter(query = "100%"))).first().size)
    }
}
