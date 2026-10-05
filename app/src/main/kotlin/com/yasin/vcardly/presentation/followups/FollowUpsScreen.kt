package com.yasin.vcardly.presentation.followups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyChip
import com.yasin.vcardly.core.designsystem.component.VCardlyChipRow
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyFab
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyScreenHeader
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.presentation.common.VCardlyFollowUpCard
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

data class FollowUpsActions(
    val onSelect: (FollowUpBucket) -> Unit = {},
    val onToggleDone: (FollowUpWithContact) -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onAdd: () -> Unit = {},
)

@Composable
fun FollowUpsScreen(
    onAddFollowUp: () -> Unit,
    onOpenFollowUp: (Long) -> Unit,
    viewModel: FollowUpsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FollowUpsContent(
        state,
        FollowUpsActions(
            onSelect = viewModel::select,
            onToggleDone = { item -> if (item.followUp.status.isActive) viewModel.complete(item.followUp.id) else viewModel.reopen(item.followUp.id) },
            onOpen = onOpenFollowUp,
            onAdd = onAddFollowUp,
        ),
    )
}

private val bucketOrder = listOf(FollowUpBucket.TODAY, FollowUpBucket.UPCOMING, FollowUpBucket.OVERDUE, FollowUpBucket.COMPLETED)

/** Stateless follow-ups content (used directly by UI tests). */
@Composable
fun FollowUpsContent(state: FollowUpsUiState, actions: FollowUpsActions) {
    val number = remember { NumberFormat.getInstance() }
    val pending = state.counts.today + state.counts.upcoming + state.counts.overdue
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            VCardlyScreenHeader(
                title = stringResource(R.string.nav_follow_ups),
                subtitle = if (state.isLoading) null else pluralStringResource(R.plurals.followups_pending, pending, number.format(pending)),
            )
            VCardlyChipRow(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                items(bucketOrder, key = { it.name }) { bucket ->
                    val count = state.counts.of(bucket)
                    VCardlyChip(
                        if (count > 0 && bucket != FollowUpBucket.COMPLETED) stringResource(R.string.followup_tab, stringResource(bucket.labelRes()), number.format(count))
                        else stringResource(bucket.labelRes()),
                        selected = bucket == state.bucket,
                        onClick = { actions.onSelect(bucket) },
                        dotColor = if (bucket == FollowUpBucket.OVERDUE && count > 0) MaterialTheme.vcColors.rose.accent else null,
                    )
                }
            }
            when {
                state.isLoading -> VCardlyLoadingState(kind = SkeletonKind.LIST)
                state.items.isEmpty() -> EmptyBucket(state.bucket, actions.onAdd)
                else -> FollowUpList(state, actions)
            }
        }
        VCardlyFab(Icons.Rounded.Add, stringResource(R.string.followup_add), actions.onAdd, Modifier.align(Alignment.BottomEnd).padding(end = MaterialTheme.spacing.screen, bottom = 20.dp))
    }
}

@Composable
private fun FollowUpList(state: FollowUpsUiState, actions: FollowUpsActions) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val startOfToday = remember { today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val dayFormat = remember { DateFormat.getDateInstance(DateFormat.FULL) }
    // Upcoming and Completed are long lists, so they get day headers; Today and Overdue are already one group.
    val grouped = state.bucket == FollowUpBucket.UPCOMING
    val groups = remember(state.items, grouped) {
        if (!grouped) listOf<Pair<LocalDate?, List<FollowUpWithContact>>>(null to state.items)
        else state.items.groupBy { Instant.ofEpochMilli(it.followUp.dueAt).atZone(zone).toLocalDate() }.toList()
    }
    val tomorrowLabel = stringResource(R.string.followup_group_tomorrow)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = MaterialTheme.spacing.screen, end = MaterialTheme.spacing.screen, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { (day, items) ->
            if (day != null) {
                item(key = "h$day") {
                    Text(
                        if (day == today.plusDays(1)) tomorrowLabel else dayFormat.format(Date.from(day.atStartOfDay(zone).toInstant())),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() }.animateItem(),
                    )
                }
            }
            items(items, key = { it.followUp.id }) { item ->
                VCardlyFollowUpCard(
                    item = item,
                    isOverdue = item.followUp.dueAt < startOfToday,
                    onClick = { actions.onOpen(item.followUp.id) },
                    onToggleDone = { actions.onToggleDone(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun EmptyBucket(bucket: FollowUpBucket, onAdd: () -> Unit) {
    val colors = MaterialTheme.vcColors
    val (icon, tone) = when (bucket) {
        FollowUpBucket.TODAY -> Icons.Rounded.Today to colors.blue
        FollowUpBucket.UPCOMING -> Icons.Rounded.Upcoming to colors.lavender
        FollowUpBucket.OVERDUE -> Icons.Rounded.TaskAlt to colors.mint
        FollowUpBucket.COMPLETED -> Icons.Rounded.EventAvailable to colors.orange
    }
    VCardlyEmptyState(
        icon = icon,
        title = stringResource(if (bucket == FollowUpBucket.OVERDUE) R.string.followup_empty_overdue_title else R.string.followup_none_title),
        message = stringResource(bucket.emptyMessageRes()),
        tone = tone,
        action = if (bucket == FollowUpBucket.TODAY || bucket == FollowUpBucket.UPCOMING) {
            { VCardlyPrimaryButton(stringResource(R.string.followup_add), onClick = onAdd, leadingIcon = Icons.Rounded.Add) }
        } else null,
    )
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
