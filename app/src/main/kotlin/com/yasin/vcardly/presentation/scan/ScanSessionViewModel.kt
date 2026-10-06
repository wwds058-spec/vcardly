package com.yasin.vcardly.presentation.scan

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppDispatchers
import com.yasin.vcardly.core.image.BitmapOps
import com.yasin.vcardly.core.image.CardImageRef
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.core.ocr.OcrEngine
import com.yasin.vcardly.domain.scan.BusinessCardParser
import com.yasin.vcardly.domain.scan.CropMath
import com.yasin.vcardly.domain.scan.NormalizedRect
import com.yasin.vcardly.domain.scan.OcrLine
import com.yasin.vcardly.presentation.contacts.ContactForm
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanSide { FRONT, BACK }
enum class ScanError { IMPORT_FAILED, IMAGE_UNREADABLE, CROP_FAILED }

data class ScanSessionState(
    val side: ScanSide = ScanSide.FRONT,
    val rawFile: File? = null,
    val preview: Bitmap? = null,
    val rotation: Int = 0,
    /** Where the card was found in [preview], or null (the crop then starts from its default inset). */
    val detected: NormalizedRect? = null,
    val frontFile: File? = null,
    val backFile: File? = null,
    val isWorking: Boolean = false,
    /** True while text recognition runs ("Reading your card..."). */
    val isReading: Boolean = false,
    val error: ScanError? = null,
)

sealed interface ScanEvent {
    data object ToCrop : ScanEvent
    /** Front side is cropped; ask whether to scan the back too. */
    data object FrontDone : ScanEvent
    data object BackToCapture : ScanEvent
    data object ToReview : ScanEvent
}

/**
 * One scan = one instance, scoped to the scan nav graph. Holds work-in-progress images in the cache
 * dir only; nothing becomes permanent until the user saves the reviewed contact.
 */
@HiltViewModel
class ScanSessionViewModel @Inject constructor(
    private val store: CardImageStore,
    private val ocr: OcrEngine,
    private val draftStore: ScanDraftStore,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanSessionState())
    val state: StateFlow<ScanSessionState> = _state.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var basePreview: Bitmap? = null

    fun newCaptureFile(): File = store.newCaptureFile()

    fun onCaptured(file: File) {
        viewModelScope.launch { prepareCrop(file) }
    }

    fun onPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val file = store.importFromUri(uri)
            if (file == null) _state.update { it.copy(isWorking = false, error = ScanError.IMPORT_FAILED) }
            else prepareCrop(file)
        }
    }

    private suspend fun prepareCrop(file: File) {
        val bitmap = store.decodeUpright(file, PREVIEW_MAX)
        if (bitmap == null) {
            _state.update { it.copy(isWorking = false, error = ScanError.IMAGE_UNREADABLE) }
            return
        }
        basePreview = bitmap
        val detected = withContext(dispatchers.default) { BitmapOps.findCard(bitmap) }
        _state.update { it.copy(rawFile = file, preview = bitmap, rotation = 0, detected = detected, isWorking = false, error = null) }
        _events.send(ScanEvent.ToCrop)
    }

    fun rotate(clockwise: Boolean) {
        val base = basePreview ?: return
        val rotation = (_state.value.rotation + if (clockwise) 90 else 270) % 360
        viewModelScope.launch {
            val (rotated, detected) = withContext(dispatchers.default) {
                BitmapOps.rotate(base, rotation).let { it to BitmapOps.findCard(it) }
            }
            _state.update { it.copy(preview = rotated, rotation = rotation, detected = detected) }
        }
    }

    /** Applies rotation + crop at high resolution to the original capture and keeps the result for this side. */
    fun confirmCrop(rect: NormalizedRect) {
        val s = _state.value
        val raw = s.rawFile ?: return
        if (s.isWorking) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val result = store.decodeUpright(raw, FULL_MAX)?.let { full ->
                withContext(dispatchers.default) {
                    val rotated = BitmapOps.rotate(full, s.rotation)
                    BitmapOps.crop(rotated, CropMath.toPixels(rect, rotated.width, rotated.height))
                }
            }?.let { store.saveToScanCache(it) }
            if (result == null) {
                _state.update { it.copy(isWorking = false, error = ScanError.CROP_FAILED) }
                return@launch
            }
            _state.update {
                if (it.side == ScanSide.FRONT) it.copy(frontFile = result, isWorking = false)
                else it.copy(backFile = result, isWorking = false)
            }
            if (s.side == ScanSide.FRONT) _events.send(ScanEvent.FrontDone) else finish()
        }
    }

    fun addBackSide() {
        _state.update { it.copy(side = ScanSide.BACK, rawFile = null, preview = null, rotation = 0, detected = null) }
        basePreview = null
        viewModelScope.launch { _events.send(ScanEvent.BackToCapture) }
    }

    fun retake() {
        _state.update { it.copy(rawFile = null, preview = null, rotation = 0, detected = null) }
        basePreview = null
        viewModelScope.launch { _events.send(ScanEvent.BackToCapture) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** "Rescan" from the review form: forget both sides and start again from the front. */
    fun restart() {
        basePreview = null
        draftStore.clear()
        store.clearScanCache()
        _state.value = ScanSessionState()
    }

    /** Runs OCR on the finished sides, builds an editable draft and moves on to the review form. */
    fun finish() {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, isReading = true) }
            val s = _state.value
            var failed = false
            val lines = mutableListOf<OcrLine>()
            listOfNotNull(s.frontFile, s.backFile).forEachIndexed { index, file ->
                val bitmap = store.decodeUpright(file, OCR_MAX)
                val side = if (bitmap == null) null else try {
                    ocr.recognize(bitmap)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (side == null) failed = true
                // Back-side lines sit below front lines so they never outrank them for the name.
                else lines += side.map { it.copy(top = it.top + index * BACK_OFFSET) }
            }
            val parsed = BusinessCardParser.parse(lines)
            val nothingFound = lines.isEmpty() || parsed.copy(unmatched = emptyList()) == com.yasin.vcardly.domain.scan.ParsedCard()
            draftStore.set(
                ScanDraft(
                    form = ContactForm(
                        fullName = parsed.fullName, jobTitle = parsed.jobTitle, company = parsed.company,
                        phone = parsed.phone, phoneAlt = parsed.phoneAlt, email = parsed.email, emailAlt = parsed.emailAlt,
                        website = parsed.website, address = parsed.address,
                        frontImage = s.frontFile?.let { CardImageRef.Pending(it.path) },
                        backImage = s.backFile?.let { CardImageRef.Pending(it.path) },
                    ),
                    front = s.frontFile,
                    back = s.backFile,
                    unmatched = parsed.unmatched,
                    ocrFailed = failed || nothingFound,
                ),
            )
            _state.update { it.copy(isWorking = false, isReading = false) }
            _events.send(ScanEvent.ToReview)
        }
    }

    override fun onCleared() {
        // Scan ended (saved or abandoned). Saved images were already persisted by the review form.
        store.clearScanCache()
        draftStore.clear()
    }

    private companion object {
        const val PREVIEW_MAX = 1600
        const val FULL_MAX = 3000
        const val OCR_MAX = 2400
        const val BACK_OFFSET = 100_000
    }
}
