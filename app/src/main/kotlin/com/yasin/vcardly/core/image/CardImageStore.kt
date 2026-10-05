package com.yasin.vcardly.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.yasin.vcardly.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Where a card image lives. Stored = permanent, app-private, relative path; Pending = scan cache file not yet saved. */
sealed interface CardImageRef {
    val path: String
    data class Stored(override val path: String) : CardImageRef
    data class Pending(override val path: String) : CardImageRef
}

/**
 * All card-image file handling. Permanent images live in `filesDir/cards` (app-private, excluded from
 * backup); scan work-in-progress lives in `cacheDir/scan` and is wiped when a scan ends. Paths and file
 * names are random UUIDs, never derived from contact data.
 */
@Singleton
class CardImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val cardsDir get() = File(context.filesDir, CARDS_DIR)
    private val scanDir get() = File(context.cacheDir, SCAN_DIR)
    private val restoreDir get() = File(context.cacheDir, RESTORE_DIR)

    /** A fresh, empty file in the scan cache for the camera to write into. */
    fun newCaptureFile(): File {
        scanDir.mkdirs()
        return File(scanDir, "${UUID.randomUUID()}.jpg")
    }

    fun clearScanCache() {
        scanDir.deleteRecursively()
    }

    /** Copies a gallery/document Uri into the scan cache. Returns null if it cannot be read. */
    suspend fun importFromUri(uri: Uri): File? = withContext(dispatchers.io) {
        val target = newCaptureFile()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            target
        } catch (_: IOException) {
            target.delete(); null
        } catch (_: SecurityException) {
            target.delete(); null
        }
    }

    /** Decodes [file] downsampled so neither side exceeds [maxDimension], with EXIF rotation applied. */
    suspend fun decodeUpright(file: File, maxDimension: Int): Bitmap? = withContext(dispatchers.io) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null
            applyExif(decoded, file)
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Writes [bitmap] as JPEG into the scan cache (not yet permanent). */
    suspend fun saveToScanCache(bitmap: Bitmap): File? = withContext(dispatchers.io) {
        val target = newCaptureFile()
        try {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            target
        } catch (_: IOException) {
            target.delete(); null
        }
    }

    /** Moves a scan-cache file into permanent storage; returns its relative path or null on failure. */
    suspend fun persist(pending: File): String? = withContext(dispatchers.io) {
        try {
            if (!pending.canonicalFile.startsWith(scanDir.canonicalFile) || !pending.isFile) return@withContext null
            cardsDir.mkdirs()
            val name = "${UUID.randomUUID()}.jpg"
            val tmp = File(cardsDir, "$name.tmp")
            pending.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(File(cardsDir, name))) {
                tmp.delete(); return@withContext null
            }
            "$CARDS_DIR/$name"
        } catch (_: IOException) {
            null
        }
    }

    /** Opens a stored image for reading (backup). Null if it does not exist or the path escapes the cards directory. */
    fun openStored(relativePath: String): java.io.InputStream? =
        resolveStored(relativePath)?.takeIf { it.isFile }?.inputStream()

    /** A fresh empty directory in the cache for unpacking a backup. */
    fun newRestoreDir(): File {
        val dir = File(restoreDir, UUID.randomUUID().toString())
        dir.mkdirs()
        return dir
    }

    fun clearRestoreCache() {
        restoreDir.deleteRecursively()
    }

    /** Copies an unpacked image into permanent storage under a new random name (merge restore). */
    suspend fun installFromRestore(staged: File): String? = withContext(dispatchers.io) {
        try {
            if (!staged.canonicalFile.startsWith(restoreDir.canonicalFile) || !staged.isFile) return@withContext null
            cardsDir.mkdirs()
            val name = "${UUID.randomUUID()}.jpg"
            staged.copyTo(File(cardsDir, name), overwrite = false)
            "$CARDS_DIR/$name"
        } catch (_: IOException) {
            null
        }
    }

    /** Replaces ALL stored images with the unpacked ones, keeping their file names (replace restore). */
    suspend fun replaceAllFromRestore(stagedDir: File): Boolean = withContext(dispatchers.io) {
        try {
            if (!stagedDir.canonicalFile.startsWith(restoreDir.canonicalFile)) return@withContext false
            cardsDir.deleteRecursively()
            cardsDir.mkdirs()
            stagedDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                if (!f.renameTo(File(cardsDir, f.name))) f.copyTo(File(cardsDir, f.name), overwrite = true)
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    suspend fun delete(relativePath: String?) {
        if (relativePath == null) return
        withContext(dispatchers.io) { resolveStored(relativePath)?.delete() }
    }

    suspend fun load(ref: CardImageRef, maxDimension: Int): Bitmap? {
        val file = when (ref) {
            is CardImageRef.Stored -> resolveStored(ref.path)
            is CardImageRef.Pending -> File(ref.path).takeIf { it.canonicalFile.startsWith(scanDir.canonicalFile) }
        } ?: return null
        return decodeUpright(file, maxDimension)
    }

    /** Resolves a stored path, refusing anything that escapes the cards directory. */
    private fun resolveStored(relativePath: String): File? {
        val file = File(context.filesDir, relativePath)
        return file.takeIf { it.canonicalFile.startsWith(cardsDir.canonicalFile) }
    }

    private fun applyExif(bitmap: Bitmap, file: File): Bitmap {
        val orientation = try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private companion object {
        const val CARDS_DIR = "cards"
        const val SCAN_DIR = "scan"
        const val RESTORE_DIR = "restore"
        const val JPEG_QUALITY = 90
    }
}

/** Pure bitmap transforms used by the scan flow. */
object BitmapOps {
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** [box] is x, y, width, height in pixels (see CropMath.toPixels). */
    fun crop(bitmap: Bitmap, box: IntArray): Bitmap = Bitmap.createBitmap(bitmap, box[0], box[1], box[2], box[3])
}
