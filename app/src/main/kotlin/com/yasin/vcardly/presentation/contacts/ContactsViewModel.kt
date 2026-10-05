package com.yasin.vcardly.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.ContactSort
import com.yasin.vcardly.domain.model.Tag
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val isLoading: Boolean = true,
    val filter: ContactFilter = ContactFilter(),
    val contacts: List<ContactDetails> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    val hasActiveFilters: Boolean
        get() = filter.query.isNotBlank() || filter.categoryId != null ||
            filter.tagIds.isNotEmpty() || filter.favoritesOnly
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contacts: ContactRepository,
    categories: CategoryRepository,
    tags: TagRepository,
    private val entitlements: com.yasin.vcardly.core.billing.EntitlementManager,
) : ViewModel() {

    private val filter = MutableStateFlow(ContactFilter())

    private val results = filter
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { contacts.observeContacts(it) }

    val uiState: StateFlow<ContactsUiState> = combine(
        filter,
        results,
        categories.observeAll(),
        tags.observeAll().map { rows -> rows.map { it.tag } },
    ) { f, list, cats, allTags ->
        ContactsUiState(isLoading = false, filter = f, contacts = list, categories = cats, tags = allTags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setSort(sort: ContactSort) = filter.update { it.copy(sort = sort) }
    fun setFavoritesOnly(value: Boolean) = filter.update { it.copy(favoritesOnly = value) }
    fun setCategory(id: Long?) = filter.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    fun toggleTag(id: Long) = filter.update {
        it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id)
    }

    /** Clears search and filters but keeps the chosen sort order. */
    fun clearFilters() = filter.update { ContactFilter(sort = it.sort) }

    /** Free plan: a monthly scan allowance. [onAllowed] starts the scanner; otherwise [onBlocked] gets the limit to show. */
    fun requestScan(onAllowed: () -> Unit, onBlocked: (limit: Int) -> Unit) {
        viewModelScope.launch {
            when (val a = entitlements.scanAllowance()) {
                com.yasin.vcardly.domain.entitlement.ScanAllowance.Unlimited -> onAllowed()
                is com.yasin.vcardly.domain.entitlement.ScanAllowance.Limited -> if (a.isAllowed) onAllowed() else onBlocked(a.limit)
            }
        }
    }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { contacts.setFavorite(id, !current) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
