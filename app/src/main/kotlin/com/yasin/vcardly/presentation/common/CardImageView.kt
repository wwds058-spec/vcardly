package com.yasin.vcardly.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyIconButton
import com.yasin.vcardly.core.image.CardImageRef

/** Provided once in MainActivity so composables can load card images without threading the store through every screen. */
val LocalCardImageStore = staticCompositionLocalOf<com.yasin.vcardly.core.image.CardImageStore?> { null }

val LocalAuthGate = staticCompositionLocalOf<com.yasin.vcardly.core.security.AuthGate> { error("AuthGate not provided") }

/** Decodes [ref] off the main thread at most [maxDimension] px; null until ready, or if the file is gone. */
@Composable
fun rememberCardBitmap(ref: CardImageRef?, maxDimension: Int = 1024): ImageBitmap? {
    val store = LocalCardImageStore.current
    var bitmap by remember(ref, maxDimension) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(ref, maxDimension) {
        bitmap = if (ref == null || store == null) null else store.load(ref, maxDimension)?.asImageBitmap()
    }
    return bitmap
}

/** Card image in a rounded frame with a soft placeholder until decoded; fades in when ready. */
@Composable
fun CardImageView(
    ref: CardImageRef,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    contentScale: ContentScale = ContentScale.Fit,
    maxDimension: Int = 1024,
) {
    val bitmap = rememberCardBitmap(ref, maxDimension)
    Box(
        modifier
            .aspectRatio(1.6f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AnimatedVisibility(bitmap != null, enter = fadeIn()) {
            bitmap?.let { Image(it, contentDescription = contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/** Full-screen viewer: pinch to zoom, drag to pan, double-tap to reset. */
@Composable
fun CardImageViewer(ref: CardImageRef, contentDescription: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bitmap = rememberCardBitmap(ref, maxDimension = 2400)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            bitmap?.let {
                Image(
                    it,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                )
            }
            VCardlyIconButton(
                Icons.Rounded.Close,
                stringResource(R.string.common_close),
                onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
            )
        }
    }
}
