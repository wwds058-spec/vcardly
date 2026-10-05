package com.yasin.vcardly.core.export

import android.content.Context
import android.net.Uri
import com.yasin.vcardly.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Writes to a document the user picked in the system file picker (no storage permission involved). */
@Singleton
class ExportWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    /** Returns true if the file was written completely. "wt" truncates, so an existing file never keeps stale bytes. */
    suspend fun write(uri: Uri, block: (OutputStream) -> Unit): Boolean = withContext(dispatchers.io) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use(block) != null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
