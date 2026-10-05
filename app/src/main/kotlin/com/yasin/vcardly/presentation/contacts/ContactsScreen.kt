package com.yasin.vcardly.presentation.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyChip
import com.yasin.vcardly.core.designsystem.component.VCardlyChipRow
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyScreenHeader
import com.yasin.vcardly.core.designsystem.component.VCardlySearchBar
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.pressScale
import com.yasin.vcardly.core.designsystem.theme.SheetShape
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.ContactSort
import com.yasin.vcardly.presentation.common.VCardlyContactCard
import com.yasin.vcardly.presentation.common.displayName

/** User intents from the Contacts list (stateless content takes these so tests can drive it). */
data class ContactsActions(
    val onQueryChange: (String) -> Unit = {},
    val onSortChange: (ContactSort) -> Unit = {},
    val onShowAll: () -> Unit = {},
    val onFavoritesOnly: () -> Unit = {},
    val onCategory: (Long) -> Unit = {},
    val onToggleTag: (Long) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onToggleFavorite: (id: Long, current: Boolean) -> Unit = { _, _ -> },
    val onOpenContact: (Long) -> Unit = {},
    val onAddContact: () -> Unit = {},
    val onScanCard: () -> Unit = {},
    val onOpenOrganize: () -> Unit = {},
)

@Composable
fun ContactsScreen(
    onOpenContact: (Long) -> Unit,
    onAddContact: () -> Unit,
    onScanCard: () -> Unit,
    onOpenOrganize: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ContactsContent(
        state = state,
        actions = ContactsActions(
            onQueryChange = viewModel::setQuery,
            onSortChange = viewModel::setSort,
            onShowAll = viewModel::showAll,
            onFavoritesOnly = { viewModel.setFavoritesOnly(!state.filter.favoritesOnly) },
            onCategory = viewModel::setCategory,
            onToggleTag = viewModel::toggleTag,
            onClearFilters = viewModel::clearFilters,
            onToggleFavorite = viewModel::toggleFavorite,
            onOpenContact = onOpenContact,
            onAddContact = onAddContact,
            onScanCard = onScanCard,
            onOpenOrganize = onOpenOrganize,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactsContent(state: ContactsUiState, actions: ContactsActions) {
    val filter = state.filter
    var tagSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            VCardlyScreenHeader(
                title = stringResource(R.string.nav_contacts),
                subtitle = if (state.isLoading) null
                else if (state.hasActiveFilters) stringResource(R.string.contacts_count, state.contacts.size)
                else pluralStringResource(R.plurals.contacts_total, state.contacts.size, state.contacts.size),
                actions = {
                    SortMenu(filter.sort, actions.onSortChange)
                    OverflowMenu(actions.onOpenOrganize)
                },
            )
            VCardlySearchBar(
                query = filter.query,
                onQueryChange = actions.onQueryChange,
                placeholder = stringResource(R.string.home_search_placeholder),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.screen),
            )
            VCardlyChipRow(Modifier.padding(top = 14.dp, bottom = 6.dp)) {
                item(key = "all") {
                    VCardlyChip(
                        stringResource(R.string.contacts_filter_all),
                        selected = !filter.favoritesOnly && filter.categoryId == null && filter.tagIds.isEmpty(),
                        onClick = actions.onShowAll,
                    )
                }
                item(key = "fav") {
                    VCardlyChip(stringResource(R.string.contacts_filter_favorites), selected = filter.favoritesOnly, onClick = actions.onFavoritesOnly, leadingIcon = Icons.Rounded.Star)
                }
                items(state.categories.size, key = { "c" + state.categories[it].id }) { i ->
                    val category = state.categories[i]
                    VCardlyChip(
                        category.displayName().asString(),
                        selected = filter.categoryId == category.id,
                        onClick = { actions.onCategory(category.id) },
                        dotColor = Color(category.colorArgb),
                    )
                }
                if (state.tags.isNotEmpty()) {
                    item(key = "tags") {
                        val selected = filter.tagIds.size
                        VCardlyChip(
                            if (selected == 0) stringResource(R.string.field_tags) else stringResource(R.string.contacts_tags_selected, selected),
                            selected = selected > 0,
                            onClick = { tagSheet = true },
                            leadingIcon = Icons.Rounded.Label,
                        )
                    }
                }
            }

            when {
                state.isLoading -> VCardlyLoadingState(kind = SkeletonKind.LIST)
                state.contacts.isEmpty() && filter.favoritesOnly && filter.query.isBlank() && filter.categoryId == null && filter.tagIds.isEmpty() -> VCardlyEmptyState(
                    icon = Icons.Rounded.Star,
                    title = stringResource(R.string.contacts_no_favorites_title),
                    message = stringResource(R.string.contacts_no_favorites_message),
                    tone = MaterialTheme.vcColors.orange,
                )
                state.contacts.isEmpty() && state.hasActiveFilters -> VCardlyEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.contacts_no_results_title),
                    message = stringResource(R.string.contacts_no_results_message),
                    action = { VCardlySecondaryButton(stringResource(R.string.contacts_clear_filters), onClick = actions.onClearFilters) },
                )
                state.contacts.isEmpty() -> VCardlyEmptyState(
                    icon = Icons.Rounded.DocumentScanner,
                    title = stringResource(R.string.empty_network_title),
                    message = stringResource(R.string.contacts_empty_message),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            VCardlyPrimaryButton(stringResource(R.string.empty_network_action), onClick = actions.onScanCard, leadingIcon = Icons.Rounded.DocumentScanner)
                            VCardlySecondaryButton(stringResource(R.string.contacts_add), onClick = actions.onAddContact, leadingIcon = Icons.Rounded.PersonAdd)
                        }
                    },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Room for the add button so it never covers the last card.
                    contentPadding = PaddingValues(start = MaterialTheme.spacing.screen, end = MaterialTheme.spacing.screen, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.contacts, key = { it.contact.id }) { details ->
                        VCardlyContactCard(
                            details = details,
                            onClick = { actions.onOpenContact(details.contact.id) },
                            onToggleFavorite = { actions.onToggleFavorite(details.contact.id, details.contact.isFavorite) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        if (!state.isLoading && state.contacts.isNotEmpty()) {
            AddFab(actions.onAddContact, Modifier.align(Alignment.BottomEnd).padding(end = MaterialTheme.spacing.screen, bottom = 20.dp))
        }
    }

    if (tagSheet) {
        ModalBottomSheet(onDismissRequest = { tagSheet = false }, shape = SheetShape, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = MaterialTheme.spacing.screen).navigationBarsPadding().padding(bottom = 24.dp)) {
                Text(stringResource(R.string.contacts_filter_by_tags), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.contacts_filter_by_tags_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tags.forEach { tag ->
                        VCardlyChip("#" + tag.name, selected = tag.id in filter.tagIds, onClick = { actions.onToggleTag(tag.id) })
                    }
                }
            }
        }
    }
}

/** Circular gradient "add" button. */
@Composable
fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier, description: String = stringResource(R.string.contacts_add)) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.vcColors
    Box(
        modifier
            .pressScale(interaction, 0.92f)
            .size(60.dp)
            .shadow(12.dp, CircleShape, ambientColor = colors.gradientPurple.last(), spotColor = colors.gradientPurple.last())
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.gradientIndigo.first(), colors.gradientPurple.last())))
            .clickable(interaction, androidx.compose.material3.ripple(color = Color.White), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SortMenu(current: ContactSort, onSortChange: (ContactSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.contacts_sort_prefix, stringResource(current.labelRes())))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ContactSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes())) },
                    onClick = { onSortChange(sort); open = false },
                    trailingIcon = if (sort == current) {
                        { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.common_selected), tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(onOpenOrganize: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_organize)) },
                leadingIcon = { Icon(Icons.Rounded.Label, contentDescription = null) },
                onClick = { open = false; onOpenOrganize() },
            )
        }
    }
}

private fun ContactSort.labelRes(): Int = when (this) {
    ContactSort.NAME_ASC -> R.string.sort_name_asc
    ContactSort.NAME_DESC -> R.string.sort_name_desc
    ContactSort.COMPANY_ASC -> R.string.sort_company
    ContactSort.RECENTLY_ADDED -> R.string.sort_recently_added
    ContactSort.RECENTLY_UPDATED -> R.string.sort_recently_updated
}
