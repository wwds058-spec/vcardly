package com.yasin.vcardly.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.EmptyIllustration
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyActionCard
import com.yasin.vcardly.core.designsystem.component.VCardlyAvatar
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySearchLauncher
import com.yasin.vcardly.core.designsystem.component.VCardlySectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyStatCard
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.entitlement.AdPlacement
import com.yasin.vcardly.domain.usecase.CategoryBreakdownItem
import com.yasin.vcardly.presentation.common.AdBanner
import com.yasin.vcardly.presentation.common.VCardlyContactMini
import com.yasin.vcardly.presentation.common.VCardlyFollowUpCard
import com.yasin.vcardly.presentation.common.displayName
import java.text.NumberFormat

/** Navigation callbacks for Home; wired in AppRoot. */
data class DashboardActions(
    val onOpenSearch: () -> Unit = {},
    val onOpenContacts: () -> Unit = {},
    val onOpenFollowUps: () -> Unit = {},
    val onOpenContact: (Long) -> Unit = {},
    val onOpenFollowUp: (Long) -> Unit = {},
    val onScan: () -> Unit = {},
    val onAddContact: () -> Unit = {},
    val onOpenMyCard: () -> Unit = {},
    val onAddFollowUp: () -> Unit = {},
    val onOpenReports: () -> Unit = {},
    val onCompleteFollowUp: (Long) -> Unit = {},
)

@Composable
fun DashboardScreen(
    onOpenSearch: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenFollowUps: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenFollowUp: (Long) -> Unit,
    onScan: () -> Unit,
    onAddContact: () -> Unit,
    onOpenMyCard: () -> Unit,
    onAddFollowUp: () -> Unit,
    onOpenReports: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = DashboardActions(
        onOpenSearch, onOpenContacts, onOpenFollowUps, onOpenContact, onOpenFollowUp, onScan, onAddContact,
        onOpenMyCard, onAddFollowUp, onOpenReports, onCompleteFollowUp = viewModel::complete,
    )
    Column(Modifier.fillMaxSize()) {
        DashboardContent(state, actions, Modifier.weight(1f))
        // Home shows aggregates and the user's own follow-ups list; the banner sits below the content, never over it.
        AdBanner(AdPlacement.HOME)
    }
}

/** Stateless Home content (used directly by screenshot/UI tests). */
@Composable
fun DashboardContent(state: DashboardUiState, actions: DashboardActions, modifier: Modifier = Modifier) {
    val number = remember { NumberFormat.getInstance() }
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.vcColors
    if (state.isLoading) {
        Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) { VCardlyLoadingState(kind = SkeletonKind.CARDS) }
        return
    }
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "header") { Header(state, actions.onOpenMyCard) }
        item(key = "search") {
            VCardlySearchLauncher(stringResource(R.string.home_search_placeholder), actions.onOpenSearch)
        }
        item(key = "stats") {
            AnimatedVisibility(appear, enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 6 }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VCardlyStatCard(
                            value = number.format(state.totalContacts),
                            label = stringResource(R.string.home_stat_contacts),
                            icon = Icons.Rounded.People,
                            tone = colors.blue,
                            supporting = if (state.addedThisMonth > 0) stringResource(R.string.home_stat_this_month, number.format(state.addedThisMonth)) else null,
                            onClick = actions.onOpenContacts,
                            modifier = Modifier.weight(1f),
                        )
                        VCardlyStatCard(
                            value = number.format(state.favorites),
                            label = stringResource(R.string.dashboard_favorites),
                            icon = Icons.Rounded.Star,
                            tone = colors.rose,
                            onClick = actions.onOpenContacts,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VCardlyStatCard(
                            value = number.format(state.pendingFollowUps),
                            label = stringResource(R.string.nav_follow_ups),
                            icon = Icons.Rounded.EventAvailable,
                            tone = colors.mint,
                            supporting = if (state.followUps.today > 0) stringResource(R.string.home_stat_due_today, number.format(state.followUps.today)) else null,
                            onClick = actions.onOpenFollowUps,
                            modifier = Modifier.weight(1f),
                        )
                        VCardlyStatCard(
                            value = number.format(state.followUps.overdue),
                            label = stringResource(R.string.followup_overdue),
                            icon = Icons.Rounded.NotificationImportant,
                            tone = colors.orange,
                            onClick = actions.onOpenFollowUps,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item(key = "quick") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VCardlySectionHeader(stringResource(R.string.home_quick_actions))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VCardlyActionCard(stringResource(R.string.contacts_scan), Icons.Rounded.DocumentScanner, colors.gradientBlue, actions.onScan, Modifier.weight(1f))
                    VCardlyActionCard(stringResource(R.string.contacts_add), Icons.Rounded.PersonAdd, colors.gradientIndigo, actions.onAddContact, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VCardlyActionCard(stringResource(R.string.settings_my_card), Icons.Rounded.Badge, colors.gradientPurple, actions.onOpenMyCard, Modifier.weight(1f))
                    VCardlyActionCard(stringResource(R.string.followup_add), Icons.Rounded.PostAdd, colors.gradientOrange, actions.onAddFollowUp, Modifier.weight(1f))
                }
            }
        }
        if (state.isEmpty) {
            item(key = "empty") { EmptyNetworkCard(actions.onScan) }
        } else {
            item(key = "upcomingHeader") {
                VCardlySectionHeader(
                    stringResource(R.string.home_upcoming_follow_ups),
                    actionLabel = stringResource(R.string.common_see_all),
                    onAction = actions.onOpenFollowUps,
                )
            }
            if (state.upcoming.isEmpty()) {
                item(key = "noFollowUps") { NoFollowUpsCard(actions.onAddFollowUp) }
            } else {
                items(state.upcoming, key = { "f" + it.followUp.id }) { item ->
                    val overdue = item.followUp.dueAt < startOfToday()
                    VCardlyFollowUpCard(
                        item = item,
                        isOverdue = overdue,
                        onClick = { actions.onOpenFollowUp(item.followUp.id) },
                        onToggleDone = { actions.onCompleteFollowUp(item.followUp.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.recent.isNotEmpty()) {
                item(key = "recentHeader") {
                    VCardlySectionHeader(
                        stringResource(R.string.home_recent_contacts),
                        actionLabel = stringResource(R.string.common_see_all),
                        onAction = actions.onOpenContacts,
                    )
                }
                item(key = "recent") {
                    // Edge-to-edge carousel that still lines up with the screen margin.
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 0.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        items(state.recent, key = { it.contact.id }) { d ->
                            VCardlyContactMini(d, onClick = { actions.onOpenContact(d.contact.id) })
                        }
                    }
                }
            }
            if (state.categories.isNotEmpty()) {
                item(key = "network") { NetworkCard(state.categories, number, actions.onOpenReports) }
            }
        }
    }
}

@Composable
private fun Header(state: DashboardUiState, onOpenMyCard: () -> Unit) {
    val greeting = stringResource(
        when (state.greeting) {
            Greeting.MORNING -> R.string.home_greeting_morning
            Greeting.AFTERNOON -> R.string.home_greeting_afternoon
            Greeting.EVENING -> R.string.home_greeting_evening
        },
    )
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(greeting, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (state.firstName.isNotBlank()) stringResource(R.string.home_greeting_name, state.firstName) else stringResource(R.string.home_greeting_no_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val openLabel = stringResource(R.string.settings_my_card)
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpenMyCard)
                .semantics { contentDescription = openLabel },
            contentAlignment = Alignment.Center,
        ) {
            if (state.myCardName.isNotBlank()) {
                VCardlyAvatar(state.myCardName, null, size = 52.dp)
            } else {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun EmptyNetworkCard(onScan: () -> Unit) {
    VCardlyCard(Modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyIllustration(Icons.Rounded.DocumentScanner, MaterialTheme.vcColors.blue)
            Text(stringResource(R.string.empty_network_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Text(
                stringResource(R.string.empty_network_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            VCardlyPrimaryButton(stringResource(R.string.empty_network_action), onClick = onScan, leadingIcon = Icons.Rounded.DocumentScanner)
        }
    }
}

@Composable
private fun NoFollowUpsCard(onAdd: () -> Unit) {
    VCardlyCard(Modifier.fillMaxWidth(), onClick = onAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.vcColors.mint.container), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = MaterialTheme.vcColors.mint.accent)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(stringResource(R.string.followup_none_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.followup_none_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Your network" mini breakdown: a stacked bar plus the top categories. */
@Composable
private fun NetworkCard(items: List<CategoryBreakdownItem>, number: NumberFormat, onOpenReports: () -> Unit) {
    VCardlyCard(Modifier.fillMaxWidth(), onClick = onOpenReports, onClickLabel = stringResource(R.string.reports_title)) {
        Text(stringResource(R.string.home_network_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.home_network_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.padding(top = 14.dp, bottom = 12.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { item ->
                val color = item.category?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline
                Box(Modifier.weight(item.fraction.coerceAtLeast(0.02f)).fillMaxHeight().background(color))
            }
        }
        items.take(4).forEach { item ->
            val name = item.category?.displayName()?.asString() ?: stringResource(R.string.category_uncategorised)
            val color = item.category?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline
            val summary = pluralStringResource(R.plurals.dashboard_category_summary, item.count, name, number.format(item.count))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = summary },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.size(10.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(number.format(item.count), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun startOfToday(): Long =
    java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
