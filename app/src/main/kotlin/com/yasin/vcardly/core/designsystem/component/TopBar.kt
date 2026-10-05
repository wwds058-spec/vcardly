package com.yasin.vcardly.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.theme.spacing

/**
 * Detail-screen top bar: a round back button, a start-aligned title and optional actions. The back arrow is
 * auto-mirrored in RTL locales. Applies the status-bar inset itself.
 */
@Composable
fun VCardlyTopBar(
    title: String,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateUp != null) {
            VCardlyIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_navigate_up),
                onClick = onNavigateUp,
                containerColor = containerColor,
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Header for the top-level tabs: a large title with an optional subtitle ("128 contacts") and trailing actions.
 * Applies the status-bar inset itself.
 */
@Composable
fun VCardlyScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = MaterialTheme.spacing.screen, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
