package com.yasin.vcardly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.CardShape
import com.yasin.vcardly.core.designsystem.theme.IconBadgeShape
import com.yasin.vcardly.core.designsystem.theme.OverlineStyle
import com.yasin.vcardly.core.designsystem.theme.StatValueStyle
import com.yasin.vcardly.core.designsystem.theme.TileShape
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors

/** Soft, brand-tinted shadow; flat in dark mode, where a hairline border separates surfaces instead. */
@Composable
fun Modifier.softShadow(shape: Shape = CardShape, elevation: Dp = 10.dp): Modifier {
    val colors = MaterialTheme.vcColors
    return if (colors.isDark) this.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
    else this.shadow(elevation, shape, clip = false, ambientColor = colors.shadow.copy(alpha = 0.10f), spotColor = colors.shadow.copy(alpha = 0.14f))
}

/** The standard elevated surface. Tappable when [onClick] is set (with press feedback and a button role). */
@Composable
fun VCardlyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = CardShape,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: Dp = MaterialTheme.spacing.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.985f) else Modifier)
            .softShadow(shape)
            .clip(shape)
            .background(color)
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = androidx.compose.material3.ripple(), onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Section title with an optional trailing action ("See all"). Marked as a heading for TalkBack. */
@Composable
fun VCardlySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            VCardlyTextButton(actionLabel, onClick = onAction)
        }
    }
}

/** Small uppercase label above a group of rows (Settings, forms). */
@Composable
fun VCardlyOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = OverlineStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/** Tinted rounded square (or circle) holding an icon, used by stats, settings rows and info rows. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    circle: Boolean = false,
    solid: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .clip(if (circle) CircleShape else IconBadgeShape)
            .background(if (solid) tone.accent else tone.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (solid) Color.White else tone.accent, modifier = Modifier.size(iconSize))
    }
}

/**
 * Dashboard / report figure on a softly tinted card. Read by TalkBack as one item ("128, Contacts, +12 this month").
 */
@Composable
fun VCardlyStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.vcColors.mint.content,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(CardShape)
            .background(tone.container)
            .then(if (onClick != null) Modifier.clickable(interaction, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(MaterialTheme.spacing.card),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(value, style = StatValueStyle, color = tone.content, modifier = Modifier.weight(1f), maxLines = 1)
            IconBadge(icon, tone.copy(container = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (MaterialTheme.vcColors.isDark) 0.35f else 0.9f)), size = 36.dp, iconSize = 18.dp, circle = true)
        }
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.labelMedium, color = supportingColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Gradient quick-action tile (Scan card, Add contact, ...). White content; every gradient stop is contrast-checked. */
@Composable
fun VCardlyActionCard(
    title: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .heightIn(min = 60.dp)
            .clip(TileShape)
            .background(Brush.linearGradient(gradient))
            .clickable(interaction, androidx.compose.material3.ripple(color = Color.White), role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(IconBadgeShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** Row used for settings and navigation lists: icon badge, title, description, chevron. */
@Composable
fun VCardlyNavigationRow(
    icon: ImageVector,
    tone: Tone,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { DefaultChevron() },
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = MaterialTheme.spacing.card, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, tone, solid = true, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun DefaultChevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Card that groups rows (Settings sections, detail info), separated by whitespace instead of heavy dividers. */
@Composable
fun VCardlyGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    VCardlyCard(modifier.fillMaxWidth(), contentPadding = 4.dp, content = content)
}
