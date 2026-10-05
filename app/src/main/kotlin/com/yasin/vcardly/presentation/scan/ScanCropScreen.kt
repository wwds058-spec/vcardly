package com.yasin.vcardly.presentation.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.scan.CropHandle
import com.yasin.vcardly.domain.scan.CropMath
import com.yasin.vcardly.domain.scan.NormalizedRect

@Composable
fun ScanCropScreen(
    session: ScanSessionViewModel,
    onRetake: () -> Unit,
    onBackToCapture: () -> Unit,
    onReview: () -> Unit,
) {
    val state by session.state.collectAsStateWithLifecycle()
    var rect by remember { mutableStateOf(NormalizedRect.Default) }
    var askBack by remember { mutableStateOf(false) }

    // A new rotation or a new image invalidates the previous selection.
    LaunchedEffect(state.rotation, state.rawFile) { rect = NormalizedRect.Default }

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

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.scan_crop_title), onNavigateUp = session::retake)

        state.error?.let {
            Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(MaterialTheme.spacing.md))
        }
        Text(
            stringResource(R.string.scan_crop_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md),
        )

        Box(Modifier.weight(1f).fillMaxWidth().padding(MaterialTheme.spacing.md), contentAlignment = Alignment.Center) {
            val preview = state.preview
            if (preview != null) CropArea(preview, rect, onRectChange = { rect = it }, enabled = !state.isWorking)
            if (state.isWorking) CircularProgressIndicator()
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VCardlyTextButton(stringResource(R.string.scan_rotate_left), onClick = { session.rotate(clockwise = false) }, enabled = !state.isWorking)
            VCardlyTextButton(stringResource(R.string.scan_rotate_right), onClick = { session.rotate(clockwise = true) }, enabled = !state.isWorking)
            // Keyboard / TalkBack users cannot drag handles, so offer whole-image and reset actions.
            VCardlyTextButton(stringResource(R.string.scan_use_full_image), onClick = { rect = NormalizedRect.Full }, enabled = !state.isWorking)
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(MaterialTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            VCardlyTextButton(stringResource(R.string.scan_retake), onClick = session::retake, enabled = !state.isWorking, modifier = Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.scan_use_crop), onClick = { session.confirmCrop(rect) }, enabled = !state.isWorking && state.preview != null, modifier = Modifier.weight(1f))
        }
    }

    if (askBack) {
        // Two explicit choices; tapping outside must not silently pick one.
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.scan_back_title)) },
            text = { Text(stringResource(R.string.scan_back_message)) },
            confirmButton = { VCardlyTextButton(stringResource(R.string.scan_back_add), onClick = { askBack = false; session.addBackSide() }) },
            dismissButton = { VCardlyTextButton(stringResource(R.string.scan_back_done), onClick = { askBack = false; session.finish() }) },
        )
    }
}

@Composable
private fun CropArea(bitmap: Bitmap, rect: NormalizedRect, onRectChange: (NormalizedRect) -> Unit, enabled: Boolean) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val ratio = bitmap.width.toFloat() / bitmap.height
    val handleRadius = with(LocalDensity.current) { 28.dp.toPx() }
    val scrim = Color.Black.copy(alpha = 0.55f)
    val accent = MaterialTheme.colorScheme.primary
    val currentRect by rememberUpdatedState(rect)
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
                            active = if (currentEnabled) CropMath.hitTest(currentRect, o.x, o.y, size.width.toFloat(), size.height.toFloat(), handleRadius) else null
                        },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, drag ->
                        change.consume()
                        active?.let { onRectChange(CropMath.drag(currentRect, it, drag.x / size.width, drag.y / size.height)) }
                    }
                },
            ) {
                val l = rect.left * size.width; val t = rect.top * size.height
                val r = rect.right * size.width; val b = rect.bottom * size.height
                // Dim everything outside the selection.
                drawRect(scrim, Offset.Zero, Size(size.width, t))
                drawRect(scrim, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(scrim, Offset(0f, t), Size(l, b - t))
                drawRect(scrim, Offset(r, t), Size(size.width - r, b - t))
                drawRect(accent, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 3.dp.toPx()))
                listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = it)
                    drawCircle(accent, radius = 8.dp.toPx(), center = it)
                }
            }
        }
    }
}
