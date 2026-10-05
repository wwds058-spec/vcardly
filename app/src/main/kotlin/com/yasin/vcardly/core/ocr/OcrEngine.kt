package com.yasin.vcardly.core.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yasin.vcardly.domain.scan.OcrLine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface OcrEngine {
    /** Recognised lines top-to-bottom. Throws on engine failure. Never logs or stores the text. */
    suspend fun recognize(bitmap: Bitmap): List<OcrLine>
}

/** On-device ML Kit (bundled Latin model). Telugu and Urdu scripts are not supported by ML Kit. */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks
            .flatMap { it.lines }
            .map { line ->
                val box = line.boundingBox
                OcrLine(text = line.text, top = box?.top ?: 0, height = box?.height() ?: 0)
            }
            .sortedBy { it.top }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
