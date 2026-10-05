package com.yasin.vcardly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.core.designsystem.theme.Tone

/**
 * One piece of information: a tinted icon, a small label and the value, with an optional tap action (call, open
 * website) and a trailing action (copy). Replaces "Label: value" forms.
 */
@Composable
fun VCardlyInfoRow(
    icon: ImageVector,
    tone: Tone,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailing: (() -> Unit)? = null,
    maxLines: Int = 3,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, tone, size = 40.dp, iconSize = 20.dp, circle = true)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
        if (trailingIcon != null && onTrailing != null) {
            IconButton(onClick = onTrailing) {
                Icon(trailingIcon, contentDescription = trailingDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** One event in an activity timeline. */
data class TimelineEntry(val icon: ImageVector, val tone: Tone, val title: String, val time: String)

/** Vertical timeline: dots joined by a thin rail, newest first. */
@Composable
fun VCardlyTimeline(entries: List<TimelineEntry>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) {}) {
                Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(entry.icon, entry.tone, size = 32.dp, iconSize = 16.dp, circle = true)
                    if (index != entries.lastIndex) {
                        Box(Modifier.width(2.dp).weight(1f).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, bottom = 18.dp, top = 4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(entry.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Progress stepper for multi-step forms: dots and labels, the current step highlighted. */
@Composable
fun VCardlyStepper(steps: List<String>, current: Int, modifier: Modifier = Modifier, onStepClick: ((Int) -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, label ->
            val done = index < current
            val active = index == current
            Column(
                Modifier
                    .weight(1f)
                    .then(if (onStepClick != null && index <= current) Modifier.clickable { onStepClick(index) } else Modifier)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(2.dp).background(if (index == 0) androidx.compose.ui.graphics.Color.Transparent else if (done || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    Box(
                        Modifier
                            .size(if (active) 20.dp else 14.dp)
                            .clip(CircleShape)
                            .background(if (done || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary))
                    }
                    Box(Modifier.weight(1f).height(2.dp).background(if (index == steps.lastIndex) androidx.compose.ui.graphics.Color.Transparent else if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
