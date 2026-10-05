package com.yasin.vcardly.core.crypto

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Wrong password, or the data was damaged / truncated / reordered (AES-GCM cannot tell these apart). */
class DecryptionException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Streaming password encryption for backups: PBKDF2-HMAC-SHA256 key derivation, then AES-256-GCM over 64 KiB chunks
 * (the "STREAM" construction). Each chunk has its own nonce (random 8-byte prefix + chunk counter) and authenticates the
 * header plus a "last chunk" flag, so tampering, reordering and truncation are all detected, and memory use stays flat
 * however large the backup is.
 *
 * File layout: "VCBK" | version(1) | kdf(1) | iterations(4) | salt(16) | noncePrefix(8) | chunks...
 */
object ChunkedAesGcm {
    val MAGIC = byteArrayOf('V'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    const val CHUNK = 64 * 1024
    const val DEFAULT_ITERATIONS = 310_000
    private const val VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    private const val SALT = 16
    private const val PREFIX = 8
    private const val TAG_BITS = 128
    private const val TAG_BYTES = 16
    private const val HEADER = 4 + 1 + 1 + 4 + SALT + PREFIX
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 5_000_000

    fun isEncrypted(firstBytes: ByteArray): Boolean = firstBytes.size >= 4 && firstBytes.copyOf(4).contentEquals(MAGIC)

    fun encryptingStream(out: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): OutputStream {
        val salt = ByteArray(SALT).also(random::nextBytes)
        val prefix = ByteArray(PREFIX).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).put(KDF_PBKDF2_SHA256).putInt(iterations).put(salt).put(prefix).array()
        out.write(header)
        return Encrypter(out, key(password, salt, iterations), header, prefix)
    }

    /** Reads and checks the header; the password itself is verified when the first chunk is decrypted. */
    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        val header = ByteArray(HEADER)
        try {
            readFully(input, header)
        } catch (e: EOFException) {
            throw DecryptionException("Truncated header", e)
        }
        val b = ByteBuffer.wrap(header)
        val magic = ByteArray(4).also { b.get(it) }
        if (!magic.contentEquals(MAGIC)) throw DecryptionException("Not an encrypted backup")
        if (b.get() != VERSION || b.get() != KDF_PBKDF2_SHA256) throw DecryptionException("Unsupported encryption version")
        val iterations = b.getInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw DecryptionException("Unsupported key settings")
        val salt = ByteArray(SALT).also { b.get(it) }
        val prefix = ByteArray(PREFIX).also { b.get(it) }
        return Decrypter(PushbackInputStream(input, 1), key(password, salt, iterations), header, prefix)
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, counter: Int, header: ByteArray, last: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
            updateAAD(byteArrayOf(if (last) 1 else 0))
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        if (n == 0 && buf.isNotEmpty()) throw EOFException()
        return n
    }

    private class Encrypter(
        private val out: OutputStream, private val key: SecretKeySpec, private val header: ByteArray, private val prefix: ByteArray,
    ) : OutputStream() {
        private val buf = ByteArray(CHUNK)
        private var len = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, n: Int) {
            var o = off
            var remaining = n
            while (remaining > 0) {
                // Only flush a full buffer once more data arrives, so the final chunk is always known at close().
                if (len == CHUNK) flush(last = false)
                val take = minOf(remaining, CHUNK - len)
                System.arraycopy(b, o, buf, len, take)
                len += take; o += take; remaining -= take
            }
        }

        private fun flush(last: Boolean) {
            val c = cipher(Cipher.ENCRYPT_MODE, key, prefix, counter++, header, last)
            out.write(c.doFinal(buf, 0, len))
            len = 0
        }

        override fun close() {
            if (closed) return
            closed = true
            try { flush(last = true) } finally { out.close() }
        }
    }

    private class Decrypter(
        private val input: PushbackInputStream, private val key: SecretKeySpec, private val header: ByteArray, private val prefix: ByteArray,
    ) : InputStream() {
        private var plain = ByteArray(0)
        private var pos = 0
        private var counter = 0
        private var done = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, n: Int): Int {
            if (n == 0) return 0
            while (pos >= plain.size) {
                if (done) return -1
                nextChunk()
            }
            val take = minOf(n, plain.size - pos)
            System.arraycopy(plain, pos, b, off, take)
            pos += take
            return take
        }

        private fun nextChunk() {
            val enc = ByteArray(CHUNK + TAG_BYTES)
            val got = try { readFully(input, enc) } catch (e: EOFException) { throw DecryptionException("Truncated backup", e) }
            var last = got < enc.size
            if (!last) { // a full chunk: it is the last one only if nothing follows it
                val peek = input.read()
                if (peek < 0) last = true else input.unread(peek)
            }
            if (got < TAG_BYTES) throw DecryptionException("Truncated backup")
            try {
                plain = cipher(Cipher.DECRYPT_MODE, key, prefix, counter++, header, last).doFinal(enc, 0, got)
            } catch (e: GeneralSecurityException) {
                throw DecryptionException("Wrong password or damaged backup", e)
            }
            pos = 0
            done = last
        }

        override fun close() = input.close()
    }
}
