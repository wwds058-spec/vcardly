package com.yasin.vcardly.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yasin.vcardly.R
import com.yasin.vcardly.core.common.AppDispatchers
import com.yasin.vcardly.core.designsystem.theme.VCardlyTheme
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.core.ocr.OcrEngine
import com.yasin.vcardly.domain.scan.OcrLine
import com.yasin.vcardly.presentation.scan.ScanCaptureScreen
import com.yasin.vcardly.presentation.scan.ScanDraftStore
import com.yasin.vcardly.presentation.scan.ScanSessionViewModel
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real capture screen with the emulator's camera (CI starts the emulator with an emulated back camera). Guards a bug
 * where the screen released the camera the moment it had been opened: a black preview and a shutter that did nothing.
 */
@RunWith(AndroidJUnit4::class)
class CameraCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before fun grantCamera() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
    }

    @Test fun cameraStaysOpen_onceTheScreenHasSettled() {
        val dispatchers = AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)
        val noOcr = object : OcrEngine {
            override suspend fun recognize(bitmap: Bitmap): List<OcrLine> = emptyList()
        }
        val session = ScanSessionViewModel(CardImageStore(context, dispatchers), noOcr, ScanDraftStore(), dispatchers)
        rule.setContent { VCardlyTheme { ScanCaptureScreen(session, onCancel = {}, onCaptured = {}) } }

        val provider = ProcessCameraProvider.getInstance(context).get()
        val cameras = provider.availableCameraInfos
        val back = CameraSelector.DEFAULT_BACK_CAMERA.filter(cameras).first()
        // Every state the camera goes through, with its error, so a failure says what happened.
        val history = java.util.Collections.synchronizedList(mutableListOf<String>())
        instrumentation.runOnMainSync {
            back.cameraState.observeForever { history += "${it.type}${it.error?.let { e -> " error=${e.code}" } ?: ""}" }
        }
        fun state(): CameraState.Type? {
            var type: CameraState.Type? = null
            instrumentation.runOnMainSync { type = back.cameraState.value?.type }
            return type
        }
        // The same binding the screen does, run directly, to surface the error the screen turns into a message.
        fun directBind(): String {
            var outcome = "bound"
            instrumentation.runOnMainSync {
                outcome = try {
                    provider.unbindAll()
                    val preview = Preview.Builder().build()
                    val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                    provider.bindToLifecycle(rule.activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    "bound"
                } catch (e: Throwable) {
                    generateSequence(e) { it.cause }.joinToString(" <- ") { it.toString() }
                }
            }
            return outcome
        }
        fun report() = "cameras=${cameras.size}, states=$history, " +
            "cameraFailedShown=${rule.onAllNodes(hasText(context.getString(R.string.scan_camera_failed))).fetchSemanticsNodes().isNotEmpty()}, " +
            "directBind=${directBind()}"

        try {
            rule.waitUntil(20_000) { state() == CameraState.Type.OPEN }
        } catch (e: Throwable) {
            throw AssertionError("camera never opened: ${report()}", e)
        }
        // Let every recomposition and effect run; the camera must still be open afterwards.
        Thread.sleep(2_000)
        rule.waitForIdle()
        assertEquals(report(), CameraState.Type.OPEN, state())
        rule.onNodeWithContentDescription(context.getString(R.string.scan_take_photo)).assertIsEnabled()
    }
}
