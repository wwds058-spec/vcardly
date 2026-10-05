package com.yasin.vcardly.presentation.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlySearchBar
import com.yasin.vcardly.core.designsystem.component.VCardlySectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.presentation.common.VCardlyContactCard
import com.yasin.vcardly.presentation.common.VCardlyFollowUpCard

/** Global search across contacts (name, company, phone, email, notes, tags, category) and follow-ups. */
@Composable
fun SearchScreen(
    onNavigateUp: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenFollowUp: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val startOfToday = remember { java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        VCardlyTopBar(title = stringResource(R.string.search_title), onNavigateUp = onNavigateUp)
        VCardlySearchBar(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            placeholder = stringResource(R.string.search_hint),
            focusRequester = focus,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.screen),
        )
        when {
            state.isBlank -> VCardlyEmptyState(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.search_prompt_title), stringResource(R.string.search_prompt_message))
            state.isEmpty -> VCardlyEmptyState(Icons.Rounded.SearchOff, stringResource(R.string.contacts_no_results_title), stringResource(R.string.search_no_results_message), tone = MaterialTheme.vcColors.orange)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.screen, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.contacts.isNotEmpty()) {
                    item(key = "h-contacts") { VCardlySectionHeader(pluralStringResource(R.plurals.search_contacts_header, state.contacts.size, state.contacts.size)) }
                    items(state.contacts, key = { "c${it.contact.id}" }) { d ->
                        VCardlyContactCard(d, onClick = { onOpenContact(d.contact.id) }, onToggleFavorite = { viewModel.toggleFavorite(d.contact.id, d.contact.isFavorite) }, showActions = false)
                    }
                }
                if (state.followUps.isNotEmpty()) {
                    item(key = "h-followups") { VCardlySectionHeader(pluralStringResource(R.plurals.search_followups_header, state.followUps.size, state.followUps.size)) }
                    items(state.followUps, key = { "f${it.followUp.id}" }) { item ->
                        VCardlyFollowUpCard(item, isOverdue = item.followUp.status.isActive && item.followUp.dueAt < startOfToday, onClick = { onOpenFollowUp(item.followUp.id) }, onToggleDone = null)
                    }
                }
            }
        }
    }
}
