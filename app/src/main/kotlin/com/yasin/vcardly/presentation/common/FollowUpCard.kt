package com.yasin.vcardly.presentation.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyTag
import com.yasin.vcardly.core.designsystem.theme.isLargeText
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.presentation.followups.icon
import com.yasin.vcardly.presentation.followups.labelRes
import com.yasin.vcardly.presentation.followups.tone
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** "Today · 4:30 PM", "Tomorrow · 11:00 AM", "12 Sep · 10:00 AM" in the device's locale and zone. */
@Composable
fun rememberDueLabel(dueAt: Long): String {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val time = remember(dueAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(dueAt)) }
    val day = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
    return when (day) {
        today -> stringResource(R.string.due_today_at, time)
        today.plusDays(1) -> stringResource(R.string.due_tomorrow_at, time)
        today.minusDays(1) -> stringResource(R.string.due_yesterday_at, time)
        else -> stringResource(R.string.due_date_at, remember(dueAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dueAt)) }, time)
    }
}

/**
 * Follow-up card: type badge, title, contact, due time (red when overdue) and a one-tap complete/reopen check that
 * animates between states.
 */
@Composable
fun VCardlyFollowUpCard(
    item: FollowUpWithContact,
    isOverdue: Boolean,
    onClick: () -> Unit,
    onToggleDone: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val f = item.followUp
    val closed = !f.status.isActive
    val colors = MaterialTheme.vcColors
    VCardlyCard(modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.action_open_followup), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(f.type.icon, f.type.tone, size = 46.dp, iconSize = 22.dp, circle = true)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp).semantics(mergeDescendants = true) {}) {
                Text(
                    f.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (isLargeText()) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (f.status == FollowUpStatus.COMPLETED) TextDecoration.LineThrough else null,
                    color = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val who = listOf(item.contactName, item.contactCompany).filter { it.isNotBlank() }.joinToString(" · ")
                if (who.isNotEmpty()) {
                    Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (isLargeText()) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (isOverdue && !closed) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(colors.rose.accent))
                        Box(Modifier.size(6.dp))
                    }
                    Text(
                        if (isOverdue && !closed) stringResource(R.string.due_overdue_prefix, rememberDueLabel(f.dueAt)) else rememberDueLabel(f.dueAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOverdue && !closed) colors.rose.content else MaterialTheme.colorScheme.primary,
                    )
                    if (f.status == FollowUpStatus.RESCHEDULED || f.status == FollowUpStatus.CANCELLED) {
                        Box(Modifier.size(8.dp))
                        VCardlyTag(stringResource(f.status.labelRes()), if (f.status == FollowUpStatus.CANCELLED) colors.rose else colors.orange)
                    }
                }
            }
            if (onToggleDone != null) {
                IconButton(onClick = onToggleDone) {
                    AnimatedContent(
                        targetState = f.status == FollowUpStatus.COMPLETED,
                        transitionSpec = { (scaleIn(tween(220)) + fadeIn(tween(220))) togetherWith fadeOut(tween(120)) },
                        label = "doneToggle",
                    ) { done ->
                        val label = stringResource(if (done || closed) R.string.followup_reopen_item else R.string.followup_complete_item, f.title)
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (done) colors.mint.accent else colors.mint.container),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (closed && !done) Icons.Rounded.Replay else Icons.Rounded.Check,
                                contentDescription = label,
                                tint = if (done) MaterialTheme.colorScheme.surface else colors.mint.content,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
