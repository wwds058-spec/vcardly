package com.yasin.vcardly.presentation.scan

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyIconButton
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.theme.Brand
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.scan.CropHandle
import com.yasin.vcardly.domain.scan.CropMath
import com.yasin.vcardly.domain.scan.CropQuad

/** Crop and rotate one captured side, then hand over to OCR. Navigates via the session's events. */
@Composable
fun ScanCropScreen(
    session: ScanSessionViewModel,
    onBackToCapture: () -> Unit,
    onReview: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    var quad by remember { mutableStateOf(state.detected ?: CropQuad.Default) }
    var askBack by remember { mutableStateOf(false) }

    // A new rotation or a new image invalidates the previous selection; start on the card when it was found.
    LaunchedEffect(state.rotation, state.rawFile, state.detected) { quad = state.detected ?: CropQuad.Default }

    LaunchedEffect(session) {
        session.events.collect { event ->
            when (event) {
                ScanEvent.FrontDone -> askBack = true
                ScanEvent.BackToCapture -> onBackToCapture()
                ScanEvent.ToReview -> onReview()
                ScanEvent.ToCrop -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF070D22))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                VCardlyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.scan_retake), session::retake, containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White)
                Text(stringResource(R.string.scan_crop_title), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(start = 8.dp).weight(1f))
            }
            Text(
                stringResource(if (state.detected != null) R.string.scan_crop_hint_found else R.string.scan_crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            state.error?.let {
                Text(stringResource(it.messageRes()), color = Color(0xFFFF8FA5), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                val preview = state.preview
                if (preview != null) CropArea(preview, quad, onQuadChange = { quad = it }, enabled = !state.isWorking)
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ScanControl(Icons.AutoMirrored.Rounded.RotateLeft, stringResource(R.string.scan_rotate_left), { session.rotate(clockwise = false) }, enabled = !state.isWorking)
                ScanControl(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.scan_rotate_right), { session.rotate(clockwise = true) }, enabled = !state.isWorking)
                // Keyboard / TalkBack users cannot drag handles, so offer a whole-image action.
                ScanControl(Icons.Rounded.CropFree, stringResource(R.string.scan_use_full_image), { quad = CropQuad.Full }, enabled = !state.isWorking)
                state.detected?.let { found ->
                    ScanControl(Icons.Rounded.DocumentScanner, stringResource(R.string.scan_use_detected), { quad = found }, enabled = !state.isWorking)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VCardlyTextButton(stringResource(R.string.scan_retake), onClick = session::retake, enabled = !state.isWorking, color = Color.White, modifier = Modifier.weight(1f))
                VCardlyPrimaryButton(
                    stringResource(R.string.scan_use_crop),
                    onClick = { session.confirmCrop(quad) },
                    enabled = !state.isWorking && state.preview != null,
                    leadingIcon = Icons.Rounded.Check,
                    containerColor = Brand.Blue,
                    modifier = Modifier.weight(1.6f),
                )
            }
        }

        AnimatedVisibility(state.isWorking, enter = fadeIn(), exit = fadeOut()) {
            ProcessingOverlay(if (state.isReading) stringResource(R.string.scan_reading) else stringResource(R.string.scan_processing))
        }
    }

    if (askBack) {
        // Two explicit choices; tapping outside must not silently pick one.
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.Flip, contentDescription = null) },
            title = { Text(stringResource(R.string.scan_back_title)) },
            text = { Text(stringResource(R.string.scan_back_message)) },
            shape = MaterialTheme.shapes.large,
            confirmButton = { VCardlyTextButton(stringResource(R.string.scan_back_add), onClick = { askBack = false; session.addBackSide() }) },
            dismissButton = { VCardlyTextButton(stringResource(R.string.scan_back_done), onClick = { askBack = false; session.finish() }) },
        )
    }
}

/** Full-screen "Reading your card..." state with a sweeping scan animation. */
@Composable
private fun ProcessingOverlay(message: String) {
    Box(Modifier.fillMaxSize().background(Color(0xE6070D22)).semantics { contentDescription = message; liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 220.dp, height = 138.dp), contentAlignment = Alignment.Center) {
                CardFrameOverlay(scanning = true)
                Icon(Icons.Rounded.DocumentScanner, null, tint = MaterialTheme.vcColors.blue.accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.size(20.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Text(message, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
internal fun CropArea(bitmap: Bitmap, quad: CropQuad, onQuadChange: (CropQuad) -> Unit, enabled: Boolean) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val ratio = bitmap.width.toFloat() / bitmap.height
    val handleRadius = with(LocalDensity.current) { 28.dp.toPx() }
    val scrim = Color.Black.copy(alpha = 0.6f)
    val accent = Brand.BlueBright
    val currentQuad by rememberUpdatedState(quad)
    val currentEnabled by rememberUpdatedState(enabled)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitWidth = if (maxWidth.value / maxHeight.value > ratio) maxHeight * ratio else maxWidth
        val fitHeight = fitWidth / ratio
        var active by remember { mutableStateOf<CropHandle?>(null) }

        Box(Modifier.size(fitWidth, fitHeight)) {
            // The cropped region has no text alternative; the screen title and buttons carry the task.
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            active = if (currentEnabled) CropMath.hitTest(currentQuad, o.x, o.y, size.width.toFloat(), size.height.toFloat(), handleRadius) else null
                        },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, drag ->
                        change.consume()
                        active?.let { onQuadChange(CropMath.drag(currentQuad, it, drag.x / size.width, drag.y / size.height)) }
                    }
                },
            ) {
                val (tl, tr, br, bl) = quad.corners.map { Offset(it.x * size.width, it.y * size.height) }
                val outline = Path().apply { moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close() }
                // Dim everything outside the selection.
                val outside = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addPath(outline)
                }
                drawPath(outside, scrim)
                drawPath(outline, Color.White, style = Stroke(width = 2.dp.toPx()))
                // Thirds guides follow the selection's shape, so they show how the card will be straightened.
                for (i in 1..2) {
                    val f = i / 3f
                    drawLine(Color.White.copy(alpha = 0.35f), lerp(tl, tr, f), lerp(bl, br, f), strokeWidth = 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.35f), lerp(tl, bl, f), lerp(tr, br, f), strokeWidth = 1.dp.toPx())
                }
                listOf(tl, tr, br, bl).forEach {
                    drawCircle(Color.White, radius = 12.dp.toPx(), center = it)
                    drawCircle(accent, radius = 8.dp.toPx(), center = it)
                }
            }
        }
    }
}
