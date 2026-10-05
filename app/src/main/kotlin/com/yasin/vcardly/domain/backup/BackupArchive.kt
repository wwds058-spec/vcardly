package com.yasin.vcardly.domain.backup

import com.yasin.vcardly.core.crypto.ChunkedAesGcm
import com.yasin.vcardly.core.crypto.DecryptionException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Why a backup could not be read. Each maps to a different message for the user. */
sealed class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class NotABackup : BackupException("Not a VCardly backup")
    class NewerVersion(val version: Int) : BackupException("Backup format $version is newer than this app understands")
    class NeedsPassword : BackupException("This backup is password protected")
    class WrongPasswordOrCorrupt(cause: Throwable? = null) : BackupException("Wrong password or damaged backup", cause)
    class Corrupt(detail: String) : BackupException("Backup is damaged: $detail")
    class TooLarge : BackupException("Backup contains more data than allowed")
}

/** An image to include; [open] is called twice (hash, then copy), so it must return a fresh stream each time. */
class BackupImage(val name: String, val open: () -> InputStream)

class BackupContents(val manifest: BackupManifest, val data: BackupData)

/**
 * Reads and writes the backup container: a ZIP with `manifest.json` (first), `data.json` and the files in `images/`, optionally wrapped
 * in password encryption ([ChunkedAesGcm]). The manifest carries SHA-256 checksums of every other file, which are verified on
 * restore. Reading is defensive: entry names are whitelisted (no zip-slip), sizes are capped (no zip bombs), and nothing is
 * trusted before it is checked.
 */
object BackupArchive {
    const val MAX_ENTRIES = 50_000
    const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024
    const val MAX_DATA_BYTES = 128L * 1024 * 1024
    const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    private val safeImageName = Regex("^images/[A-Za-z0-9._-]{1,100}$")

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun write(
        data: BackupData,
        images: List<BackupImage>,
        appVersion: String,
        createdAt: Long,
        out: OutputStream,
        password: CharArray?,
    ) {
        val dataBytes = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)
        val files = linkedMapOf(BackupFormat.DATA to sha256(dataBytes))
        images.forEach { img -> files[BackupFormat.IMAGES_DIR + img.name] = img.open().use(::sha256) }
        val manifest = BackupManifest(
            format = BackupFormat.ID,
            formatVersion = BackupFormat.FORMAT_VERSION,
            createdAt = createdAt,
            appVersion = appVersion,
            encrypted = password != null,
            counts = BackupCounts(data.contacts.size, data.categories.size, data.tags.size, data.followUps.size, images.size),
            files = files,
        )
        val target = if (password != null) ChunkedAesGcm.encryptingStream(out, password) else out
        ZipOutputStream(target).use { zip ->
            fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            put(BackupFormat.MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            put(BackupFormat.DATA, dataBytes)
            images.forEach { img ->
                zip.putNextEntry(ZipEntry(BackupFormat.IMAGES_DIR + img.name))
                img.open().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Reads only the manifest (cheap; used to show a summary before restoring). Verifies the password for encrypted files. */
    fun inspect(input: InputStream, password: CharArray?): BackupManifest {
        val zip = openZip(input, password)
        val entry = nextEntry(zip) ?: throw BackupException.NotABackup()
        if (entry.name != BackupFormat.MANIFEST) throw BackupException.NotABackup()
        return parseManifest(readLimited(zip, MAX_MANIFEST_BYTES))
    }

    /**
     * Reads the whole backup, verifies every checksum and returns the parsed data. Image bytes are streamed into
     * [sink] (given the bare file name) so large backups never sit in memory.
     */
    fun read(input: InputStream, password: CharArray?, sink: (name: String) -> OutputStream): BackupContents {
        val zip = openZip(input, password)
        var manifest: BackupManifest? = null
        var dataBytes: ByteArray? = null
        val seen = mutableMapOf<String, String>()
        var total = 0L
        var entries = 0
        try {
            while (true) {
                val entry = nextEntry(zip) ?: break
                if (++entries > MAX_ENTRIES) throw BackupException.TooLarge()
                when {
                    entry.isDirectory -> Unit
                    entry.name == BackupFormat.MANIFEST -> manifest = parseManifest(readLimited(zip, MAX_MANIFEST_BYTES))
                    manifest == null -> throw BackupException.NotABackup() // the manifest must come first
                    entry.name == BackupFormat.DATA -> {
                        dataBytes = readLimited(zip, MAX_DATA_BYTES)
                        total += dataBytes.size
                        seen[BackupFormat.DATA] = sha256(dataBytes)
                    }
                    safeImageName.matches(entry.name) -> {
                        val digest = MessageDigest.getInstance("SHA-256")
                        var size = 0L
                        sink(entry.name.removePrefix(BackupFormat.IMAGES_DIR)).use { out ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                size += n
                                if (size > MAX_IMAGE_BYTES) throw BackupException.TooLarge()
                                digest.update(buf, 0, n); out.write(buf, 0, n)
                            }
                        }
                        total += size
                        seen[entry.name] = hex(digest.digest())
                    }
                    else -> Unit // unknown entries are ignored, never extracted
                }
                if (total > MAX_TOTAL_BYTES) throw BackupException.TooLarge()
            }
        } catch (e: DecryptionException) {
            throw BackupException.WrongPasswordOrCorrupt(e)
        } catch (e: java.util.zip.ZipException) {
            throw BackupException.Corrupt("container")
        }
        val m = manifest ?: throw BackupException.NotABackup()
        val bytes = dataBytes ?: throw BackupException.Corrupt("missing data")
        // Integrity: everything the manifest promises must be present and unchanged.
        m.files.forEach { (name, expected) ->
            if (seen[name] != expected) throw BackupException.Corrupt("checksum mismatch")
        }
        if (m.files[BackupFormat.DATA] == null) throw BackupException.Corrupt("data is not covered by a checksum")
        val data = try {
            json.decodeFromString(BackupData.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupException.Corrupt("data")
        } catch (e: IllegalArgumentException) {
            throw BackupException.Corrupt("data")
        }
        return BackupContents(m, data)
    }

    private fun openZip(input: InputStream, password: CharArray?): ZipInputStream {
        val pushback = PushbackInputStream(input, 4)
        val head = ByteArray(4)
        var n = 0
        while (n < 4) { val r = pushback.read(head, n, 4 - n); if (r < 0) break; n += r }
        pushback.unread(head, 0, n)
        if (n < 4) throw BackupException.NotABackup()
        val source: InputStream = when {
            ChunkedAesGcm.isEncrypted(head) -> {
                if (password == null) throw BackupException.NeedsPassword()
                try { ChunkedAesGcm.decryptingStream(pushback, password) } catch (e: DecryptionException) { throw BackupException.WrongPasswordOrCorrupt(e) }
            }
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> pushback
            else -> throw BackupException.NotABackup()
        }
        return ZipInputStream(source)
    }

    private fun nextEntry(zip: ZipInputStream): ZipEntry? = try {
        zip.nextEntry
    } catch (e: DecryptionException) {
        throw BackupException.WrongPasswordOrCorrupt(e)
    } catch (e: java.util.zip.ZipException) {
        throw BackupException.NotABackup()
    }

    private fun parseManifest(bytes: ByteArray): BackupManifest {
        val m = try {
            json.decodeFromString(BackupManifest.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupException.NotABackup()
        } catch (e: IllegalArgumentException) {
            throw BackupException.NotABackup()
        }
        if (m.format != BackupFormat.ID) throw BackupException.NotABackup()
        if (m.formatVersion > BackupFormat.FORMAT_VERSION) throw BackupException.NewerVersion(m.formatVersion)
        return m
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > max) throw BackupException.TooLarge()
                out.write(buf, 0, n)
            }
        } catch (e: DecryptionException) {
            throw BackupException.WrongPasswordOrCorrupt(e)
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun sha256(input: InputStream): String {
        val d = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; d.update(buf, 0, n) }
        return hex(d.digest())
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
