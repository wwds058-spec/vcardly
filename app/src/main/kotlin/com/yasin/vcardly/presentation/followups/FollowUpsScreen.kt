package com.yasin.vcardly.presentation.followups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.LoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.model.FollowUpWithContact
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun FollowUpsScreen(
    onAddFollowUp: () -> Unit,
    onOpenFollowUp: (Long) -> Unit,
    viewModel: FollowUpsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<FollowUpWithContact?>(null) }
    val buckets = listOf(FollowUpBucket.OVERDUE, FollowUpBucket.TODAY, FollowUpBucket.UPCOMING, FollowUpBucket.COMPLETED)
    val number = remember { NumberFormat.getInstance() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            VCardlyTopBar(title = stringResource(R.string.nav_follow_ups))
            ScrollableTabRow(selectedTabIndex = buckets.indexOf(state.bucket), edgePadding = 0.dp) {
                buckets.forEach { bucket ->
                    Tab(
                        selected = bucket == state.bucket,
                        onClick = { viewModel.select(bucket) },
                        text = { Text(stringResource(R.string.followup_tab, stringResource(bucket.labelRes()), number.format(state.counts.of(bucket)))) },
                    )
                }
            }

            when {
                state.isLoading -> LoadingState()
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.followup_empty_title),
                    message = stringResource(state.bucket.emptyMessageRes()),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.items, key = { it.followUp.id }) { item ->
                        FollowUpRow(
                            item = item,
                            onClick = { onOpenFollowUp(item.followUp.id) },
                            onToggleDone = {
                                if (state.bucket == FollowUpBucket.COMPLETED) viewModel.reopen(item.followUp.id)
                                else viewModel.complete(item.followUp.id)
                            },
                            onDelete = { toDelete = item },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddFollowUp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacing.md),
        ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.followup_add)) }
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.followup_delete_title),
            message = stringResource(R.string.followup_delete_message, item.followUp.title),
            confirmText = stringResource(R.string.contact_delete),
            onConfirm = { viewModel.delete(item.followUp.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun FollowUpRow(item: FollowUpWithContact, onClick: () -> Unit, onToggleDone: () -> Unit, onDelete: () -> Unit) {
    val f = item.followUp
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val done = f.status == com.yasin.vcardly.domain.model.FollowUpStatus.COMPLETED
    val toggleLabel = stringResource(if (done) R.string.followup_reopen_item else R.string.followup_complete_item, f.title)

    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClickLabel = stringResource(R.string.action_open_followup), onClick = onClick)
            .padding(start = MaterialTheme.spacing.md, end = MaterialTheme.spacing.xs, top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.followup_row_subtitle, stringResource(f.type.labelRes()), item.contactName),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(formatter.format(Date(f.dueAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onToggleDone) {
            Icon(if (done) Icons.Filled.Refresh else Icons.Filled.Check, contentDescription = toggleLabel)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.followup_delete_item, f.title))
        }
    }
}

private fun FollowUpCounts.of(b: FollowUpBucket): Int = when (b) {
    FollowUpBucket.OVERDUE -> overdue
    FollowUpBucket.TODAY -> today
    FollowUpBucket.UPCOMING -> upcoming
    FollowUpBucket.COMPLETED -> completed
}

private fun FollowUpBucket.emptyMessageRes(): Int = when (this) {
    FollowUpBucket.OVERDUE -> R.string.followup_empty_overdue
    FollowUpBucket.TODAY -> R.string.followup_empty_today
    FollowUpBucket.UPCOMING -> R.string.followup_empty_upcoming
    FollowUpBucket.COMPLETED -> R.string.followup_empty_completed
}
