package com.yasin.vcardly.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.vcColors

/** One tab of the bottom bar. */
data class BottomNavItem(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Bottom bar with a raised circular Scan action in the middle. The four tabs are the app's top-level destinations;
 * Scan is an action (it opens the scanner flow), not a fifth destination, so it never shows as "selected".
 */
@Composable
fun VCardlyBottomNavigation(
    items: List<BottomNavItem>,
    selectedKey: String?,
    onSelect: (BottomNavItem) -> Unit,
    scanLabel: String,
    onScan: () -> Unit,
    scanIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val half = items.size / 2
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), clip = false, ambientColor = MaterialTheme.vcColors.shadow.copy(alpha = 0.12f), spotColor = MaterialTheme.vcColors.shadow.copy(alpha = 0.16f))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(68.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.take(half).forEach { NavTab(it, it.key == selectedKey, onSelect, Modifier.weight(1f)) }
            Box(Modifier.weight(1f))
            items.drop(half).forEach { NavTab(it, it.key == selectedKey, onSelect, Modifier.weight(1f)) }
        }
        ScanButton(scanLabel, scanIcon, onScan, Modifier.align(Alignment.TopCenter).offset(y = (-18).dp))
    }
}

@Composable
private fun NavTab(item: BottomNavItem, selected: Boolean, onSelect: (BottomNavItem) -> Unit, modifier: Modifier) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabColor",
    )
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab) { onSelect(item) }
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(width = 44.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ScanButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.vcColors
    Box(
        modifier
            .pressScale(interaction, 0.92f)
            .size(64.dp)
            .shadow(14.dp, CircleShape, ambientColor = colors.gradientBlue.last(), spotColor = colors.gradientBlue.last())
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors.gradientBlue))
            .clickable(interaction, androidx.compose.material3.ripple(color = Color.White), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
