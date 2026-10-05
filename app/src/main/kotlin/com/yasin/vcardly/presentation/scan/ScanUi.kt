package com.yasin.vcardly.presentation.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.Brand

/** Where the card frame sits inside a [width] x [height] viewport: 86% wide, card proportions, slightly above centre. */
fun cardFrame(width: Float, height: Float): Rect {
    val w = width * 0.86f
    val h = w / 1.6f
    val left = (width - w) / 2f
    val top = (height - h) / 2f - height * 0.04f
    return Rect(left, top, left + w, top + h)
}

/**
 * Scanner overlay: dims everything outside the card frame, draws white corner brackets, and while [scanning] sweeps a
 * glowing line across the card. Decorative (the instruction text carries the meaning).
 */
@Composable
fun CardFrameOverlay(scanning: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scanLine")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val frame = cardFrame(size.width, size.height)
        val radius = 18.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, frame.topLeft, frame.size, CornerRadius(radius), blendMode = BlendMode.Clear)

        val arm = 34.dp.toPx()
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        val l = frame.left; val t = frame.top; val r = frame.right; val b = frame.bottom
        fun corner(path: androidx.compose.ui.graphics.Path.() -> Unit) = drawPath(androidx.compose.ui.graphics.Path().apply(path), Color.White, style = stroke)
        corner { moveTo(l, t + arm); lineTo(l, t + radius); quadraticTo(l, t, l + radius, t); lineTo(l + arm, t) }
        corner { moveTo(r - arm, t); lineTo(r - radius, t); quadraticTo(r, t, r, t + radius); lineTo(r, t + arm) }
        corner { moveTo(l, b - arm); lineTo(l, b - radius); quadraticTo(l, b, l + radius, b); lineTo(l + arm, b) }
        corner { moveTo(r - arm, b); lineTo(r - radius, b); quadraticTo(r, b, r, b - radius); lineTo(r, b - arm) }

        if (scanning) {
            val y = t + (b - t) * sweep
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Brand.BlueBright.copy(alpha = 0.45f), Color.Transparent), startY = y - 40f, endY = y + 40f),
                topLeft = Offset(l + 8f, y - 40f),
                size = Size(r - l - 16f, 80f),
            )
            drawLine(Color.White.copy(alpha = 0.9f), Offset(l + 12f, y), Offset(r - 12f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Translucent pill used for instructions over the camera. */
@Composable
fun ScanPill(text: String, modifier: Modifier = Modifier, live: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    )
}

/** Round control on the dark scanner chrome, with a label underneath. */
@Composable
fun ScanControl(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.16f else 0.06f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.padding(top = 6.dp))
    }
}

/** Big white shutter ring. */
@Composable
fun ShutterButton(description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}
