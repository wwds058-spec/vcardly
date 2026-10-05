package com.yasin.vcardly.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.data.repository.ContactRepositoryImpl
import com.yasin.vcardly.data.repository.FollowUpRepositoryImpl
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpBucket
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FollowUpRepositoryTest {
    private lateinit var db: VCardlyDatabase
    private val zone = ZoneId.of("UTC")
    // 2026-06-10 12:00 UTC
    private val now = Instant.parse("2026-06-10T12:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), zone)
    private lateinit var repo: FollowUpRepositoryImpl
    private var contactId = 0L
    private val hour = 3_600_000L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VCardlyDatabase::class.java).build()
        repo = FollowUpRepositoryImpl(db, clock)
        contactId = ContactRepositoryImpl(db, clock).save(Contact(fullName = "Asha"), emptySet())
    }

    @After
    fun tearDown() = db.close()

    private fun f(title: String, due: Long) = FollowUp(contactId = contactId, title = title, dueAt = due)

    @Test
    fun buckets_splitByLocalDay_andStatus() = runBlocking {
        repo.save(f("yesterday", now - 24 * hour))      // overdue
        repo.save(f("earlier today", now - 2 * hour))    // 10:00 today -> today (not overdue until the day ends)
        repo.save(f("later today", now + 5 * hour))      // today
        repo.save(f("tomorrow", now + 15 * hour))        // upcoming
        val done = repo.save(f("done", now + hour))
        repo.markCompleted(done)

        assertEquals(listOf("yesterday"), repo.observe(FollowUpBucket.OVERDUE).first().map { it.followUp.title })
        assertEquals(listOf("earlier today", "later today"), repo.observe(FollowUpBucket.TODAY).first().map { it.followUp.title })
        assertEquals(listOf("tomorrow"), repo.observe(FollowUpBucket.UPCOMING).first().map { it.followUp.title })
        assertEquals(listOf("done"), repo.observe(FollowUpBucket.COMPLETED).first().map { it.followUp.title })
        val counts = repo.observeCounts().first()
        assertEquals(listOf(1, 2, 1, 1), listOf(counts.overdue, counts.today, counts.upcoming, counts.completed))
    }

    @Test
    fun notifiedFlag_resetsOnlyWhenScheduleChanges() = runBlocking {
        val id = repo.save(f("call", now + hour))
        repo.markNotified(id, now)
        val saved = repo.get(id)!!
        assertNotNull(saved.notifiedAt)

        repo.save(saved.copy(title = "call back"))                 // title only: stays notified
        assertNotNull(repo.get(id)!!.notifiedAt)
        repo.save(repo.get(id)!!.copy(dueAt = now + 2 * hour))     // schedule moved: may notify again
        assertNull(repo.get(id)!!.notifiedAt)

        repo.markNotified(id, now)
        repo.markCompleted(id)
        repo.reopen(id)
        assertNull(repo.get(id)!!.notifiedAt)
    }

    @Test
    fun reminderCandidates_onlyPendingWithReminders() = runBlocking {
        repo.save(f("a", now + hour))
        repo.save(f("b", now + hour).copy(reminderEnabled = false))
        val c = repo.save(f("c", now + hour)); repo.markCompleted(c)
        assertEquals(listOf("a"), repo.getReminderCandidates().map { it.title })
    }

    @Test
    fun deletingContact_cascadesToFollowUps() = runBlocking {
        val id = repo.save(f("x", now + hour))
        ContactRepositoryImpl(db, clock).delete(contactId)
        assertNull(repo.get(id))
    }
}
