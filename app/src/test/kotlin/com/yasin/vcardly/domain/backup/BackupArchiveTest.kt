package com.yasin.vcardly.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupArchiveTest {
    private val data = BackupData(
        categories = listOf(BCategory(1, "", 0xFF3F51B5, "client", 0, 5), BCategory(7, "Investors", 0xFF000000, null, 6, 6)),
        tags = listOf(BTag(1, "VIP", null, 1)),
        contacts = listOf(
            BContact(1, "Asha Rao", company = "Acme", email = "a@acme.com", categoryId = 1, tagIds = listOf(1), frontImage = "f1.jpg", isFavorite = true, notes = "హరి\nline2"),
            BContact(2, "Bilal", categoryId = 7),
        ),
        followUps = listOf(BFollowUp(1, 1, "Call back", dueAt = 99)),
        myCard = BMyCard(fullName = "Me"),
    )
    private val imageBytes = ByteArray(100_000) { (it % 251).toByte() }
    private val images = listOf(BackupImage("f1.jpg") { ByteArrayInputStream(imageBytes) })
    private val pw = "pass-1234".toCharArray()

    private fun write(password: CharArray? = null, d: BackupData = data, imgs: List<BackupImage> = images): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.write(d, imgs, "0.1.0", 1234, out, password)
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray, password: CharArray? = null): Pair<BackupContents, Map<String, ByteArray>> {
        val sunk = mutableMapOf<String, ByteArrayOutputStream>()
        val c = BackupArchive.read(ByteArrayInputStream(bytes), password) { name -> ByteArrayOutputStream().also { sunk[name] = it } }
        return c to sunk.mapValues { it.value.toByteArray() }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private inline fun <reified E : BackupException> assertFails(block: () -> Unit) {
        try { block(); fail("expected ${E::class.simpleName}") } catch (e: BackupException) { assertTrue("was ${e::class.simpleName}", e is E) }
    }

    @Test fun roundTrip_plain() {
        val (c, files) = read(write())
        assertEquals(data, c.data)
        assertEquals(BackupFormat.ID, c.manifest.format)
        assertEquals(BackupCounts(2, 2, 1, 1, 1), c.manifest.counts)
        assertFalse(c.manifest.encrypted)
        assertArrayEquals(imageBytes, files.getValue("f1.jpg"))
    }

    @Test fun roundTrip_encrypted_needsAndChecksPassword() {
        val bytes = write(pw)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Asha"))
        assertEquals(data, read(bytes, pw).first.data)
        assertTrue(BackupArchive.inspect(ByteArrayInputStream(bytes), pw).encrypted)
        assertFails<BackupException.NeedsPassword> { read(bytes, null) }
        assertFails<BackupException.WrongPasswordOrCorrupt> { read(bytes, "nope".toCharArray()) }
        assertFails<BackupException.WrongPasswordOrCorrupt> { BackupArchive.inspect(ByteArrayInputStream(bytes), "nope".toCharArray()) }
    }

    @Test fun inspect_readsOnlyTheSummary() {
        val m = BackupArchive.inspect(ByteArrayInputStream(write()), null)
        assertEquals(1234, m.createdAt); assertEquals("0.1.0", m.appVersion); assertEquals(2, m.counts.contacts)
    }

    @Test fun tamperedImage_failsChecksum() {
        val bytes = write(imgs = listOf(BackupImage("f1.jpg") { ByteArrayInputStream(imageBytes) }))
        // Rewrite the archive with a different image but the original manifest.
        val (c, _) = read(bytes)
        val forged = zip(
            BackupFormat.MANIFEST to BackupArchive.json.encodeToString(BackupManifest.serializer(), c.manifest).toByteArray(),
            BackupFormat.DATA to BackupArchive.json.encodeToString(BackupData.serializer(), c.data).toByteArray(),
            "images/f1.jpg" to ByteArray(100) { 1 },
        )
        assertFails<BackupException.Corrupt> { read(forged) }
    }

    @Test fun tamperedData_failsChecksum() {
        val (c, _) = read(write())
        val forged = zip(
            BackupFormat.MANIFEST to BackupArchive.json.encodeToString(BackupManifest.serializer(), c.manifest).toByteArray(),
            BackupFormat.DATA to BackupArchive.json.encodeToString(BackupData.serializer(), c.data.copy(contacts = emptyList())).toByteArray(),
            "images/f1.jpg" to imageBytes,
        )
        assertFails<BackupException.Corrupt> { read(forged) }
    }

    @Test fun missingPromisedImage_isCorrupt() {
        val (c, _) = read(write())
        val forged = zip(
            BackupFormat.MANIFEST to BackupArchive.json.encodeToString(BackupManifest.serializer(), c.manifest).toByteArray(),
            BackupFormat.DATA to BackupArchive.json.encodeToString(BackupData.serializer(), c.data).toByteArray(),
        )
        assertFails<BackupException.Corrupt> { read(forged) }
    }

    @Test fun zipSlipAndUnknownEntriesAreNeverExtracted() {
        val (c, _) = read(write(imgs = emptyList(), d = data.copy(contacts = emptyList())))
        val m = c.manifest
        val forged = zip(
            BackupFormat.MANIFEST to BackupArchive.json.encodeToString(BackupManifest.serializer(), m).toByteArray(),
            BackupFormat.DATA to BackupArchive.json.encodeToString(BackupData.serializer(), c.data).toByteArray(),
            "images/../../evil.jpg" to byteArrayOf(1),
            "../evil.txt" to byteArrayOf(1),
            "/abs/evil" to byteArrayOf(1),
            "images/sub/dir.jpg" to byteArrayOf(1),
            "random.bin" to byteArrayOf(1),
        )
        val sunk = mutableListOf<String>()
        BackupArchive.read(ByteArrayInputStream(forged), null) { n -> sunk += n; ByteArrayOutputStream() }
        assertTrue("nothing may be extracted, got $sunk", sunk.isEmpty())
    }

    @Test fun notABackup_variants() {
        assertFails<BackupException.NotABackup> { read(ByteArray(0)) }
        assertFails<BackupException.NotABackup> { read("hello world, not a zip".toByteArray()) }
        assertFails<BackupException.NotABackup> { read(zip("readme.txt" to byteArrayOf(1))) }
        assertFails<BackupException.NotABackup> { read(zip(BackupFormat.MANIFEST to """{"format":"other","formatVersion":1}""".toByteArray())) }
        assertFails<BackupException.NotABackup> { read(zip(BackupFormat.MANIFEST to "{not json".toByteArray())) }
        assertFails<BackupException.NotABackup> { read(zip(BackupFormat.DATA to "{}".toByteArray())) } // data before manifest
    }

    @Test fun newerFormatVersion_isRefusedClearly() {
        val m = """{"format":"vcardly-backup-v1","formatVersion":2}""".toByteArray()
        val e = try { read(zip(BackupFormat.MANIFEST to m)); null } catch (e: BackupException.NewerVersion) { e }
        assertEquals(2, e!!.version)
    }

    @Test fun unknownFieldsFromFutureMinorVersionsAreIgnored() {
        val (c, _) = read(write(imgs = emptyList(), d = BackupData()))
        val manifestJson = BackupArchive.json.encodeToString(BackupManifest.serializer(), c.manifest).dropLast(1) + ""","futureField":{"x":1}}"""
        val dataJson = """{"contacts":[],"somethingNew":[1,2,3]}"""
        val dataHash = java.security.MessageDigest.getInstance("SHA-256").digest(dataJson.toByteArray()).joinToString("") { "%02x".format(it) }
        val patchedManifest = manifestJson.replace(c.manifest.files.getValue(BackupFormat.DATA), dataHash)
        val parsed = read(zip(BackupFormat.MANIFEST to patchedManifest.toByteArray(), BackupFormat.DATA to dataJson.toByteArray())).first
        assertTrue(parsed.data.contacts.isEmpty())
    }

    @Test fun emptyBackup_isValid() {
        val (c, _) = read(write(d = BackupData(), imgs = emptyList()))
        assertEquals(BackupData(), c.data)
        assertNull(c.data.myCard)
    }

    @Test fun oversizedImageIsRejected() {
        val big = object : java.io.InputStream() { var left = BackupArchive.MAX_IMAGE_BYTES + 10; override fun read() = if (left-- > 0) 0 else -1 }
        val bytes = write(d = BackupData(), imgs = listOf(BackupImage("big.jpg") { object : java.io.InputStream() { var left = BackupArchive.MAX_IMAGE_BYTES + 10; override fun read() = if (left-- > 0) 0 else -1 } }))
        assertFails<BackupException.TooLarge> { read(bytes) }
    }
}
