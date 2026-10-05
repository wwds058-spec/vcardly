package com.yasin.vcardly.presentation.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.usecase.FollowUpManager
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
    private val imageStore: CardImageStore,
    followUpRepository: FollowUpRepository,
    private val followUpManager: FollowUpManager,
) : ViewModel() {
    private val contactId: Long = checkNotNull(savedStateHandle[ARG_CONTACT_ID])

    val uiState: StateFlow<ContactDetailUiState> = contacts.observeContact(contactId)
        .map { if (it == null) ContactDetailUiState.NotFound else ContactDetailUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailUiState.Loading)

    val followUps: StateFlow<List<FollowUp>> = followUpRepository.observeForContact(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun completeFollowUp(id: Long) { viewModelScope.launch { followUpManager.complete(id) } }
    fun reopenFollowUp(id: Long) { viewModelScope.launch { followUpManager.reopen(id) } }

    fun toggleFavorite() {
        val current = (uiState.value as? ContactDetailUiState.Content)?.details?.contact ?: return
        viewModelScope.launch { contacts.setFavorite(current.id, !current.isFavorite) }
    }

    /** Deletes the contact and then its card image files (so a failed DB delete never orphans a record). */
    fun delete(onDone: () -> Unit) {
        val contact = (uiState.value as? ContactDetailUiState.Content)?.details?.contact
        viewModelScope.launch {
            contacts.delete(contactId)
            imageStore.delete(contact?.frontImagePath)
            imageStore.delete(contact?.backImagePath)
            onDone()
        }
    }

    companion object {
        const val ARG_CONTACT_ID = "contactId"
    }
}
