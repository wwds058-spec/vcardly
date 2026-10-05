package com.yasin.vcardly.presentation.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ContactDetailUiState {
    data object Loading : ContactDetailUiState
    data object NotFound : ContactDetailUiState
    data class Content(val details: ContactDetails) : ContactDetailUiState
}

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contacts: ContactRepository,
) : ViewModel() {
    private val contactId: Long = checkNotNull(savedStateHandle[ARG_CONTACT_ID])

    val uiState: StateFlow<ContactDetailUiState> = contacts.observeContact(contactId)
        .map { if (it == null) ContactDetailUiState.NotFound else ContactDetailUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailUiState.Loading)

    fun toggleFavorite() {
        val current = (uiState.value as? ContactDetailUiState.Content)?.details?.contact ?: return
        viewModelScope.launch { contacts.setFavorite(current.id, !current.isFavorite) }
    }

    /** Card images are app-private files; their cleanup is added with image storage in Phase 4. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            contacts.delete(contactId)
            onDone()
        }
    }

    companion object {
        const val ARG_CONTACT_ID = "contactId"
    }
}
