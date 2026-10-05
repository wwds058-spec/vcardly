package com.yasin.vcardly.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChunkedAesGcmTest {
    private val pw = "correct horse battery".toCharArray()
    private val fast = 10_000 // minimum iterations the reader accepts; keeps tests quick

    private fun encrypt(plain: ByteArray, password: CharArray = pw): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkedAesGcm.encryptingStream(out, password, fast).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(enc: ByteArray, password: CharArray = pw): ByteArray =
        ChunkedAesGcm.decryptingStream(ByteArrayInputStream(enc), password).use { it.readBytes() }

    private fun sample(n: Int) = ByteArray(n).also { Random(n.toLong()).nextBytes(it) }

    @Test fun roundTrip_acrossChunkBoundaries() {
        val c = ChunkedAesGcm.CHUNK
        listOf(0, 1, 100, c - 1, c, c + 1, 2 * c, 2 * c + 7, 5 * c + 123).forEach { n ->
            val plain = sample(n)
            assertArrayEquals("size $n", plain, decrypt(encrypt(plain)))
        }
    }

    @Test fun smallWritesAreBufferedCorrectly() {
        val plain = sample(200_000)
        val out = ByteArrayOutputStream()
        ChunkedAesGcm.encryptingStream(out, pw, fast).use { s -> plain.toList().chunked(777).forEach { s.write(it.toByteArray()) } }
        assertArrayEquals(plain, decrypt(out.toByteArray()))
    }

    @Test fun wrongPasswordIsRejected() {
        val enc = encrypt(sample(1000))
        try { decrypt(enc, "wrong".toCharArray()); fail() } catch (_: DecryptionException) {}
    }

    @Test fun ciphertextDoesNotContainPlaintext_andDiffersEachTime() {
        val plain = "SECRET CONTACT NAME ".repeat(50).toByteArray()
        val a = encrypt(plain); val b = encrypt(plain)
        assertFalse(String(a, Charsets.ISO_8859_1).contains("SECRET"))
        assertFalse(a.contentEquals(b)) // fresh salt and nonce prefix
        assertTrue(ChunkedAesGcm.isEncrypted(a))
    }

    @Test fun anyBitFlipIsDetected_includingTheHeader() {
        val enc = encrypt(sample(3000))
        listOf(0, 5, 10, 20, 34, 100, enc.size - 1).forEach { i ->
            val bad = enc.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            try { decrypt(bad); fail("flip at $i went unnoticed") } catch (_: DecryptionException) {}
        }
    }

    @Test fun truncationAtAChunkBoundaryIsDetected() {
        val c = ChunkedAesGcm.CHUNK
        val enc = encrypt(sample(3 * c + 10))
        val header = 34; val encChunk = c + 16
        val cut = enc.copyOf(header + 2 * encChunk) // drop the final chunk exactly
        try { decrypt(cut); fail() } catch (_: DecryptionException) {}
        try { decrypt(enc.copyOf(enc.size - 5)); fail() } catch (_: DecryptionException) {}
    }

    @Test fun reorderedChunksAreDetected() {
        val c = ChunkedAesGcm.CHUNK
        val enc = encrypt(sample(3 * c + 10))
        val header = 34; val encChunk = c + 16
        val a = enc.copyOfRange(header, header + encChunk)
        val b = enc.copyOfRange(header + encChunk, header + 2 * encChunk)
        val swapped = enc.copyOf().also { a.copyInto(it, header + encChunk); b.copyInto(it, header) }
        try { decrypt(swapped); fail() } catch (_: DecryptionException) {}
    }

    @Test fun notEncryptedData_isNotMistaken() {
        assertFalse(ChunkedAesGcm.isEncrypted(byteArrayOf(0x50, 0x4B, 3, 4)))
        try { decrypt(ByteArray(10)); fail() } catch (_: DecryptionException) {}
    }

    @Test fun absurdIterationCountIsRefused() {
        val enc = encrypt(sample(10)).copyOf()
        enc[6] = 0x7F; enc[7] = 0x7F // iterations field -> huge
        try { decrypt(enc); fail() } catch (_: DecryptionException) {}
    }
}
