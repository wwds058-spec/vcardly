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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.concurrent.futures.await
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.component.SecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing

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
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) session.onPicked(uri)
    }
    val pickFromGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val title = stringResource(if (state.side == ScanSide.FRONT) R.string.scan_title_front else R.string.scan_title_back)

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = title, onNavigateUp = onCancel)

        if (state.error != null) {
            Text(
                stringResource(state.error!!.messageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(MaterialTheme.spacing.md),
            )
        }

        if (hasPermission) {
            CameraContent(session = session, isWorking = state.isWorking, onPickFromGallery = pickFromGallery)
        } else {
            EmptyState(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.scan_permission_title),
                message = stringResource(R.string.scan_permission_message),
                modifier = Modifier.weight(1f),
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        PrimaryButton(stringResource(R.string.scan_permission_allow), onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) })
                        // After a denial Android may stop showing the dialog; settings is the only way back.
                        if (askedOnce) SecondaryButton(stringResource(R.string.scan_permission_settings), onClick = { context.openAppSettings() })
                        SecondaryButton(stringResource(R.string.scan_pick_gallery), onClick = pickFromGallery)
                    }
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CameraContent(
    session: ScanSessionViewModel,
    isWorking: Boolean,
    onPickFromGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        try {
            val p = ProcessCameraProvider.getInstance(context).await()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            p.unbindAll()
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            provider = p
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            cameraFailed = true
        }
    }
    DisposableEffect(provider) { onDispose { provider?.unbindAll() } }

    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (cameraFailed) {
            Text(
                stringResource(R.string.scan_camera_failed),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(MaterialTheme.spacing.lg),
            )
        }
    }

    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(MaterialTheme.spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VCardlyTextButton(stringResource(R.string.scan_pick_gallery), onClick = onPickFromGallery, enabled = !isWorking && !capturing)

        val shutterLabel = stringResource(R.string.scan_take_photo)
        val enabled = !capturing && !isWorking && !cameraFailed && provider != null
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                .clickable(enabled = enabled) {
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
                }
                .semantics {
                    contentDescription = shutterLabel
                    role = Role.Button
                },
        )
        // Keeps the shutter centred opposite the gallery button.
        Box(Modifier.size(48.dp))
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
