package com.yasin.vcardly.core.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yasin.vcardly.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Hands a vCard to another app through the system share sheet. The file lives in the cache dir and is exposed
 * with a one-off read grant (FileProvider); the previous export is deleted before each new one.
 */
@Singleton
class VCardFileSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    /** Returns a chooser intent, or null if the file could not be written. */
    suspend fun createShareIntent(vCardText: String, chooserTitle: String): Intent? = withContext(dispatchers.io) {
        try {
            val dir = File(context.cacheDir, EXPORT_DIR)
            dir.deleteRecursively()
            dir.mkdirs()
            val file = File(dir, FILE_NAME).also { it.writeText(vCardText, Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/x-vcard")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .setClipData(ClipData.newRawUri("", uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Intent.createChooser(send, chooserTitle)
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val FILE_NAME = "contact.vcf"
    }
}
