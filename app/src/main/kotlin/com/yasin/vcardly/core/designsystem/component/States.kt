package com.yasin.vcardly.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.theme.CardShape
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors

/** What a skeleton should look like while content loads. */
enum class SkeletonKind { LIST, CARDS, DETAIL }

/**
 * Loading placeholder. With [message] (e.g. "Reading your card...") a spinner and the message are shown and
 * announced; otherwise a shimmering skeleton of the expected layout appears instead of a generic progress bar.
 */
@Composable
fun VCardlyLoadingState(modifier: Modifier = Modifier, message: String? = null, kind: SkeletonKind = SkeletonKind.LIST) {
    val label = message ?: stringResource(R.string.common_loading)
    if (message != null) {
        Column(
            modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite; contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            Text(message, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = MaterialTheme.spacing.md))
        }
        return
    }
    val shimmer = shimmerBrush()
    Column(
        modifier.fillMaxSize().padding(MaterialTheme.spacing.screen).semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (kind) {
            SkeletonKind.LIST -> repeat(6) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(shimmer))
                    Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(shimmer))
                        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                    }
                }
            }
            SkeletonKind.CARDS -> repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).height(108.dp).clip(CardShape).background(shimmer))
                    Box(Modifier.weight(1f).height(108.dp).clip(CardShape).background(shimmer))
                }
            }
            SkeletonKind.DETAIL -> {
                Box(Modifier.fillMaxWidth().height(200.dp).clip(CardShape).background(shimmer))
                Box(Modifier.fillMaxWidth(0.6f).height(24.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth(0.4f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                repeat(4) { Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(shimmer)) }
            }
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    return Brush.linearGradient(listOf(base, highlight, base), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + 400f, 200f))
}

/**
 * Designed empty state: a layered illustration (tinted blobs, a tilted "card" and the icon), a friendly title, a short
 * explanation and up to two actions. The illustration is decorative; the title and message carry the meaning.
 */
@Composable
fun VCardlyEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: Tone = MaterialTheme.vcColors.blue,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.xl, vertical = MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration(icon, tone)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = MaterialTheme.spacing.lg).widthIn(max = 360.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.sm).widthIn(max = 340.dp),
        )
        if (action != null) {
            Box(Modifier.padding(top = MaterialTheme.spacing.lg)) { action() }
        }
    }
}

@Composable
fun EmptyIllustration(icon: ImageVector, tone: Tone, modifier: Modifier = Modifier) {
    Box(modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).clip(CircleShape).background(tone.container))
        Box(Modifier.size(34.dp).offset(x = 62.dp, y = (-50).dp).clip(CircleShape).background(tone.accent.copy(alpha = 0.16f)))
        Box(Modifier.size(18.dp).offset(x = (-66).dp, y = 48.dp).clip(CircleShape).background(tone.accent.copy(alpha = 0.22f)))
        // A tilted card behind the icon hints at the product (visiting cards).
        Box(
            Modifier.size(width = 104.dp, height = 66.dp).rotate(-10f).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(tone.accent.copy(alpha = 0.35f), tone.accent.copy(alpha = 0.12f)))),
        )
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size(32.dp))
        }
    }
}

/** Friendly error: never shows a raw exception, always offers the next step. */
@Composable
fun VCardlyErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.common_error_title),
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    VCardlyEmptyState(
        icon = Icons.Rounded.ErrorOutline,
        title = title,
        message = message,
        modifier = modifier,
        tone = MaterialTheme.vcColors.rose,
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                onRetry?.let { VCardlyPrimaryButton(text = stringResource(R.string.common_retry), onClick = it) }
                secondaryAction?.invoke()
            }
        },
    )
}

/** Inline, non-blocking banner for warnings/notices inside a screen. */
@Composable
fun VCardlyNotice(text: String, tone: Tone, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tone.container).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tone.content.takeOrElse(MaterialTheme.colorScheme.onSurface))
    }
}

private fun Color.takeOrElse(other: Color): Color = if (this == Color.Unspecified) other else this
