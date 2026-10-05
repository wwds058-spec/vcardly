package com.yasin.vcardly.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.Brand

/** The VCardly "V" mark: two folded ribbons, light blue over deep blue. Decorative. */
@Composable
fun VCardlyMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val w = this.size.width
        val h = this.size.height
        val left = Path().apply {
            moveTo(0f, h * 0.08f)
            lineTo(w * 0.30f, h * 0.08f)
            lineTo(w * 0.56f, h * 0.70f)
            lineTo(w * 0.42f, h * 0.96f)
            close()
        }
        val right = Path().apply {
            moveTo(w * 0.42f, h * 0.96f)
            lineTo(w * 0.70f, h * 0.08f)
            lineTo(w, h * 0.08f)
            lineTo(w * 0.58f, h * 0.96f)
            close()
        }
        drawPath(left, Brush.linearGradient(listOf(Brand.BlueBright, Brand.Blue), start = Offset.Zero, end = Offset(w, h)))
        drawPath(right, Brush.linearGradient(listOf(Brand.Navy, Brand.Blue), start = Offset(w, 0f), end = Offset(0f, h)))
    }
}

/** Mark + "VCardly" wordmark, announced once as the app name. */
@Composable
fun VCardlyLogo(appName: String, modifier: Modifier = Modifier, markSize: Dp = 30.dp) {
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = appName }, verticalAlignment = Alignment.CenterVertically) {
        VCardlyMark(size = markSize)
        Spacer(Modifier.width(8.dp))
        Text(appName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clearAndSetSemantics { })
    }
}
