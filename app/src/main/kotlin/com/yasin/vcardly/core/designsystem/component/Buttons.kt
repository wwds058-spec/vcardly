package com.yasin.vcardly.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.PillShape
import com.yasin.vcardly.core.designsystem.theme.vcColors

private val PrimaryHeight = 56.dp
private val SecondaryHeight = 52.dp

/** Subtle press feedback shared by buttons and tappable tiles. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, label = "pressScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * The one obvious action on a screen: a full pill in the deep brand colour. [loading] keeps the button's size and
 * swaps the label for a spinner (the label stays as the accessibility text).
 */
@Composable
fun VCardlyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    containerColor: Color = MaterialTheme.vcColors.cta,
    contentColor: Color = MaterialTheme.vcColors.onCta,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = PillShape,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = modifier.heightIn(min = PrimaryHeight).pressScale(interaction),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
        } else {
            ButtonContent(text, leadingIcon, trailingIcon)
        }
    }
}

@Composable
fun VCardlySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        interactionSource = interaction,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = SecondaryHeight).pressScale(interaction),
    ) {
        ButtonContent(text, leadingIcon, null)
    }
}

/** Tonal pill: soft blue fill, for secondary actions that still deserve weight (e.g. "Share"). */
@Composable
fun VCardlyTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = SecondaryHeight).pressScale(interaction),
    ) {
        ButtonContent(text, leadingIcon, null)
    }
}

@Composable
fun VCardlyTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Round tonal icon button (44dp visual inside the 48dp touch target). */
@Composable
fun VCardlyIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
        modifier = modifier.size(maxOf(size, 48.dp)),
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(containerColor), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: ImageVector?, trailingIcon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
