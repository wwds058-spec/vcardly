package com.yasin.vcardly.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.yasin.vcardly.core.designsystem.theme.spacing

/** Section title; marked as a heading for screen-reader navigation. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(vertical = MaterialTheme.spacing.sm)
            .semantics { heading() },
    )
}

/** One number + label, read by TalkBack as a single item ("12, Contacts"). */
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.md)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors) { content() }
    else Card(modifier = modifier, colors = colors) { content() }
}
