package com.yasin.vcardly.domain.backup

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Reads the shared fixtures in testdata/backup (made by tools/backup_reference.py). The iOS tests read the same files, so
 * these prove that the two apps agree on the backup format.
 */
class BackupFixtureCompatTest {
    private val dir = listOf(File("../testdata/backup"), File("testdata/backup")).first { it.isDirectory }
    private val password = "correct horse".toCharArray()

    private fun read(name: String, pw: CharArray?): Pair<BackupContents, Map<String, ByteArray>> {
        val sunk = mutableMapOf<String, ByteArrayOutputStream>()
        val c = File(dir, name).inputStream().buffered().use { input ->
            BackupArchive.read(input, pw) { n -> ByteArrayOutputStream().also { sunk[n] = it } }
        }
        return c to sunk.mapValues { it.value.toByteArray() }
    }

    @Test fun plainFixture() {
        val (c, images) = read("sample-plain.vcbackup", null)
        assertEquals(2, c.data.contacts.size)
        assertEquals(3, c.data.categories.size)
        assertEquals("హరి — met at the expo\nsecond line", c.data.contacts.first { it.id == 11L }.notes)
        assertEquals(listOf(1L, 2L), c.data.contacts.first { it.id == 11L }.tagIds)
        assertEquals("RESCHEDULED", c.data.followUps.first { it.id == 21L }.status)
        assertEquals("Sam Lee", c.data.myCard?.fullName)
        assertEquals(setOf("front-11.jpg"), images.keys)
    }

    @Test fun encryptedFixtures() {
        assertEquals(2, read("sample-encrypted.vcbackup", password).first.data.contacts.size)
        val (c, images) = read("sample-multichunk.vcbackup", password)
        assertTrue(c.manifest.encrypted)
        assertEquals(150_000, images.getValue("back-12.jpg").size)
    }

    @Test fun encryptedFixtureRejectsWrongPassword() {
        try {
            read("sample-encrypted.vcbackup", "wrong password".toCharArray())
            fail("expected failure")
        } catch (_: BackupException.WrongPasswordOrCorrupt) {
        }
        try {
            read("sample-encrypted.vcbackup", null)
            fail("expected failure")
        } catch (_: BackupException.NeedsPassword) {
        }
    }
}
