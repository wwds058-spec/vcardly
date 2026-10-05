package com.yasin.vcardly.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.image.CardImageRef
import com.yasin.vcardly.core.image.CardImageStore

/** Provided once in MainActivity so composables can load card images without threading the store through every screen. */
val LocalCardImageStore = staticCompositionLocalOf<CardImageStore> { error("CardImageStore not provided") }

/** Decodes off the main thread; shows an empty placeholder until ready or if the file is gone. */
@Composable
fun CardImageView(ref: CardImageRef, contentDescription: String, modifier: Modifier = Modifier) {
    val store = LocalCardImageStore.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, ref) {
        value = store.load(ref, maxDimension = 1024)?.asImageBitmap()
    }
    Box(
        modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(it, contentDescription = contentDescription, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
