package com.yasin.vcardly.presentation.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.search.SearchMatcher
import com.yasin.vcardly.presentation.common.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val contacts: List<ContactDetails> = emptyList(),
    val followUps: List<FollowUpWithContact> = emptyList(),
) {
    val isBlank: Boolean get() = query.isBlank()
    val isEmpty: Boolean get() = contacts.isEmpty() && followUps.isEmpty()
}

/** One box that searches contacts (name, company, phone, email, notes, tags, category) and follow-ups (title, notes, contact). */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactRepository,
    private val categories: CategoryRepository,
    private val followUps: FollowUpRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private data class Results(val contacts: List<ContactDetails>, val followUps: List<FollowUpWithContact>)

    private val results = query.debounce(200).flatMapLatest { q ->
        if (q.isBlank()) flowOf(Results(emptyList(), emptyList()))
        else combine(contactMatches(q), followUps.observeAll()) { c, f -> Results(c, SearchMatcher.followUps(q, f)) }
    }

    val uiState: StateFlow<SearchUiState> = combine(query, results) { q, r -> SearchUiState(q, if (q.isBlank()) emptyList() else r.contacts, if (q.isBlank()) emptyList() else r.followUps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(q: String) { query.value = q }

    /** Text matches first, then contacts whose category name (as shown in the current language) matches. */
    private fun contactMatches(q: String) = combine(
        contacts.observeContacts(ContactFilter(query = q)),
        categories.observeAll().flatMapLatest { cats ->
            val ids = SearchMatcher.categoryIds(q, cats.associate { it.id to it.displayName().asString(context) })
            if (ids.isEmpty()) flowOf(emptyList())
            else combine(ids.map { contacts.observeContacts(ContactFilter(categoryId = it)) }) { lists -> lists.toList().flatten() }
        },
    ) { text, byCategory -> (text + byCategory).distinctBy { it.contact.id } }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { contacts.setFavorite(id, !current) }
    }
}
