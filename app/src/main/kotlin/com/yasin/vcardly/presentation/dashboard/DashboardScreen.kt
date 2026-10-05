package com.yasin.vcardly.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.LoadingState
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.component.SecondaryButton
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.StatCard
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.usecase.CategoryBreakdownItem
import com.yasin.vcardly.presentation.common.displayName
import java.text.NumberFormat

@Composable
fun DashboardScreen(
    onOpenMyCard: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenFollowUps: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.nav_home))
        when {
            state.isLoading -> LoadingState()
            state.isEmpty -> EmptyState(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.dashboard_empty_title),
                message = stringResource(R.string.dashboard_empty_message),
                action = { PrimaryButton(stringResource(R.string.dashboard_empty_action), onClick = onOpenContacts) },
            )
            else -> DashboardContent(state, onOpenContacts, onOpenFollowUps, onOpenMyCard)
        }
    }
}

@Composable
private fun DashboardContent(state: DashboardUiState, onOpenContacts: () -> Unit, onOpenFollowUps: () -> Unit, onOpenMyCard: () -> Unit) {
    // Locale-aware digits (e.g. Arabic-Indic for Urdu/Hindi locales that use them).
    val number = remember { NumberFormat.getInstance() }
    val spacing = MaterialTheme.spacing

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SectionHeader(stringResource(R.string.dashboard_section_contacts))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatCard(stringResource(R.string.dashboard_total), number.format(state.totalContacts), Modifier.weight(1f), onClick = onOpenContacts)
            StatCard(stringResource(R.string.dashboard_favorites), number.format(state.favorites), Modifier.weight(1f))
            StatCard(stringResource(R.string.dashboard_added_30_days), number.format(state.addedRecently), Modifier.weight(1f))
        }

        SectionHeader(stringResource(R.string.dashboard_section_follow_ups))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatCard(stringResource(R.string.followup_overdue), number.format(state.followUps.overdue), Modifier.weight(1f), onClick = onOpenFollowUps)
            StatCard(stringResource(R.string.followup_today), number.format(state.followUps.today), Modifier.weight(1f), onClick = onOpenFollowUps)
            StatCard(stringResource(R.string.followup_upcoming), number.format(state.followUps.upcoming), Modifier.weight(1f), onClick = onOpenFollowUps)
        }

        if (state.categories.isNotEmpty()) {
            SectionHeader(stringResource(R.string.dashboard_section_categories))
            state.categories.forEach { CategoryRow(it, number) }
        }

        SecondaryButton(stringResource(R.string.settings_my_card), onClick = onOpenMyCard, modifier = Modifier.fillMaxWidth().padding(top = spacing.md))
    }
}

@Composable
private fun CategoryRow(item: CategoryBreakdownItem, number: NumberFormat) {
    val name = item.category?.displayName()?.asString() ?: stringResource(R.string.category_uncategorised)
    val color = item.category?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline
    val summary = pluralStringResource(R.plurals.dashboard_category_summary, item.count, name, number.format(item.count))

    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(name, Modifier.weight(1f).padding(start = MaterialTheme.spacing.sm), style = MaterialTheme.typography.bodyLarge)
            Text(number.format(item.count), style = MaterialTheme.typography.bodyLarge)
        }
        // Track + fill; width is the share of all contacts.
        Box(
            Modifier
                .padding(top = MaterialTheme.spacing.xs)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(item.fraction.coerceIn(0.02f, 1f)).background(color))
        }
    }
}
