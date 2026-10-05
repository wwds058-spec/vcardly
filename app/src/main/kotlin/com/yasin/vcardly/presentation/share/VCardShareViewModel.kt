package com.yasin.vcardly.presentation.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppDispatchers
import com.yasin.vcardly.core.qr.QrMatrix
import com.yasin.vcardly.core.share.VCardFileSharer
import com.yasin.vcardly.domain.vcard.ShareCard
import com.yasin.vcardly.domain.vcard.ShareField
import com.yasin.vcardly.domain.vcard.VCardWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class VCardShareViewModel @Inject constructor(
    private val sharer: VCardFileSharer,
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : ViewModel() {
    /** Builds the vCard from the chosen fields only and hands the share intent to [onReady] (null = failed). */
    fun share(card: ShareCard, selected: Set<ShareField>, chooserTitle: String, onReady: (Intent?) -> Unit) {
        viewModelScope.launch {
            onReady(sharer.createShareIntent(VCardWriter.write(card.toVCard(selected)), chooserTitle))
        }
    }

    /**
     * Writes the QR code as a PNG to a file the user picked (Storage Access Framework). [onDone] gets true only
     * when the file was actually written.
     */
    fun saveQr(uri: Uri, matrix: QrMatrix, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(dispatchers.io) {
                try {
                    val bitmap = render(matrix)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            onDone(ok)
        }
    }

    private fun render(matrix: QrMatrix): Bitmap {
        val quiet = 4
        val module = 16
        val px = (matrix.size + quiet * 2) * module
        val pixels = IntArray(px * px) { Color.WHITE }
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!matrix[x, y]) continue
            for (dy in 0 until module) {
                val row = ((y + quiet) * module + dy) * px
                for (dx in 0 until module) pixels[row + (x + quiet) * module + dx] = Color.BLACK
            }
        }
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
    }
}
