package com.yasin.vcardly.presentation.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.EmptyIllustration
import com.yasin.vcardly.core.designsystem.component.VCardlyIconButton
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.theme.vcColors

/** Camera capture (or gallery import) for one side of a card. Navigates onward via the session's events. */
@Composable
fun ScanCaptureScreen(
    session: ScanSessionViewModel,
    onCancel: () -> Unit,
    onCaptured: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(session) {
        session.events.collect { if (it == ScanEvent.ToCrop) onCaptured() }
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) session.onPicked(uri)
    }
    val pickFromGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Asked in context: the user just chose to scan, so the request explains itself.
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val title = stringResource(if (state.side == ScanSide.FRONT) R.string.scan_title_front else R.string.scan_title_back)

    if (hasPermission) {
        CameraContent(session, state, title, onCancel, pickFromGallery)
    } else {
        PermissionContent(
            askedOnce = askedOnce,
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onSettings = { context.openAppSettings() },
            onGallery = pickFromGallery,
            onCancel = onCancel,
        )
    }
}

private enum class FlashSetting(val mode: Int) { AUTO(ImageCapture.FLASH_MODE_AUTO), ON(ImageCapture.FLASH_MODE_ON), OFF(ImageCapture.FLASH_MODE_OFF) }

@Composable
private fun CameraContent(
    session: ScanSessionViewModel,
    state: ScanSessionState,
    title: String,
    onCancel: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var flashIndex by rememberSaveable { mutableIntStateOf(0) }
    val flash = FlashSetting.entries[flashIndex]

    LaunchedEffect(lifecycleOwner) {
        try {
            val p = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            p.unbindAll()
            val camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            hasFlash = camera.cameraInfo.hasFlashUnit()
            provider = p
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            cameraFailed = true
        }
    }
    LaunchedEffect(flash, hasFlash) { if (hasFlash) imageCapture.flashMode = flash.mode }
    DisposableEffect(provider) { onDispose { provider?.unbindAll() } }

    val busy = capturing || state.isWorking
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CardFrameOverlay(scanning = busy)

        // Top chrome
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VCardlyIconButton(Icons.Rounded.Close, stringResource(R.string.common_close), onCancel, containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White)
            Spacer(Modifier.weight(1f))
            ScanPill(title)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val message = when {
                cameraFailed -> stringResource(R.string.scan_camera_failed)
                state.error != null -> stringResource(state.error.messageRes())
                busy -> stringResource(R.string.scan_capturing)
                else -> stringResource(R.string.scan_instruction)
            }
            ScanPill(message, Modifier.padding(horizontal = 24.dp), live = true)
            Row(
                Modifier.fillMaxWidth().padding(top = 28.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScanControl(Icons.Rounded.PhotoLibrary, stringResource(R.string.scan_gallery), onPickFromGallery, enabled = !busy)
                ShutterButton(
                    description = stringResource(R.string.scan_take_photo),
                    enabled = !busy && !cameraFailed && provider != null,
                    onClick = {
                        capturing = true
                        val file = session.newCaptureFile()
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    capturing = false
                                    session.onCaptured(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                    file.delete()
                                }
                            },
                        )
                    },
                )
                ScanControl(
                    icon = when (flash) { FlashSetting.AUTO -> Icons.Rounded.FlashAuto; FlashSetting.ON -> Icons.Rounded.FlashOn; FlashSetting.OFF -> Icons.Rounded.FlashOff },
                    label = stringResource(
                        when (flash) { FlashSetting.AUTO -> R.string.scan_flash_auto; FlashSetting.ON -> R.string.scan_flash_on; FlashSetting.OFF -> R.string.scan_flash_off },
                    ),
                    onClick = { flashIndex = (flashIndex + 1) % FlashSetting.entries.size },
                    enabled = hasFlash && !busy,
                )
            }
        }
    }
}

@Composable
private fun PermissionContent(askedOnce: Boolean, onAllow: () -> Unit, onSettings: () -> Unit, onGallery: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(8.dp)) {
            VCardlyIconButton(Icons.Rounded.Close, stringResource(R.string.common_close), onCancel)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyIllustration(if (askedOnce) Icons.Rounded.NoPhotography else Icons.Rounded.PhotoCamera, MaterialTheme.vcColors.blue)
            Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
            Text(
                stringResource(R.string.scan_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VCardlyPrimaryButton(stringResource(R.string.scan_permission_allow), onClick = onAllow, leadingIcon = Icons.Rounded.PhotoCamera, modifier = Modifier.fillMaxWidth())
            VCardlySecondaryButton(stringResource(R.string.scan_pick_gallery), onClick = onGallery, leadingIcon = Icons.Rounded.PhotoLibrary, modifier = Modifier.fillMaxWidth())
            // After a denial Android may stop showing the dialog; settings is the only way back.
            if (askedOnce) VCardlyTextButton(stringResource(R.string.scan_permission_settings), onClick = onSettings, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

internal fun ScanError.messageRes(): Int = when (this) {
    ScanError.IMPORT_FAILED -> R.string.scan_error_import
    ScanError.IMAGE_UNREADABLE -> R.string.scan_error_unreadable
    ScanError.CROP_FAILED -> R.string.scan_error_crop
}
