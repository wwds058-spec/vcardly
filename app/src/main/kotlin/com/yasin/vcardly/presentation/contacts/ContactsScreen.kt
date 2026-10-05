package com.yasin.vcardly.presentation.contacts

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.LoadingState
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.component.SecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactSort
import com.yasin.vcardly.presentation.common.ContactAvatar
import com.yasin.vcardly.presentation.common.displayName

@Composable
fun ContactsScreen(
    onOpenContact: (Long) -> Unit,
    onAddContact: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter = state.filter

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            VCardlyTopBar(title = stringResource(R.string.nav_contacts))

            OutlinedTextField(
                value = filter.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.contacts_clear_search))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.md),
            )

            FilterRow(state, viewModel)

            when {
                state.isLoading -> LoadingState()
                state.contacts.isEmpty() && state.hasActiveFilters -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.contacts_no_results_title),
                    message = stringResource(R.string.contacts_no_results_message),
                    action = { SecondaryButton(stringResource(R.string.contacts_clear_filters), onClick = viewModel::clearFilters) },
                )
                state.contacts.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.contacts_empty_title),
                    message = stringResource(R.string.contacts_empty_message),
                    action = { PrimaryButton(stringResource(R.string.contacts_add), onClick = onAddContact) },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Extra bottom space so the last row is not hidden behind the FAB.
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(state.contacts, key = { it.contact.id }) { details ->
                        ContactRow(
                            details = details,
                            onClick = { onOpenContact(details.contact.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(details.contact.id, details.contact.isFavorite) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddContact,
            modifier = Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacing.md),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.contacts_add))
        }
    }
}

@Composable
private fun FilterRow(state: ContactsUiState, viewModel: ContactsViewModel) {
    val filter = state.filter
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.contacts_count, state.contacts.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text(stringResource(R.string.contacts_sort_prefix, stringResource(filter.sort.labelRes())))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                ContactSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes())) },
                        onClick = {
                            viewModel.setSort(sort)
                            sortMenuOpen = false
                        },
                        leadingIcon = if (sort == filter.sort) {
                            { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_selected)) }
                        } else null,
                    )
                }
            }
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        item(key = "favorites") {
            FilterChip(
                selected = filter.favoritesOnly,
                onClick = { viewModel.setFavoritesOnly(!filter.favoritesOnly) },
                label = { Text(stringResource(R.string.contacts_filter_favorites)) },
            )
        }
        items(state.categories, key = { "c${it.id}" }) { category ->
            FilterChip(
                selected = filter.categoryId == category.id,
                onClick = { viewModel.setCategory(category.id) },
                label = { Text(category.displayName().asString()) },
            )
        }
        items(state.tags, key = { "t${it.id}" }) { tag ->
            FilterChip(
                selected = tag.id in filter.tagIds,
                onClick = { viewModel.toggleTag(tag.id) },
                label = { Text("#${tag.name}") },
            )
        }
    }
}

@Composable
private fun ContactRow(details: ContactDetails, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val contact = details.contact
    val subtitle = listOf(contact.jobTitle, contact.company).filter { it.isNotBlank() }.joinToString(" · ")
    val favoriteLabel = stringResource(
        if (contact.isFavorite) R.string.contact_remove_favorite else R.string.contact_add_favorite,
        contact.fullName,
    )

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(contact.fullName, details.category?.colorArgb)
        Column(Modifier.weight(1f).padding(horizontal = MaterialTheme.spacing.md)) {
            Text(contact.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (contact.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = favoriteLabel,
                tint = if (contact.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
