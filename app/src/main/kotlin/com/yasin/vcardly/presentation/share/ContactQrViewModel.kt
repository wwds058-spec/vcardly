package com.yasin.vcardly.presentation.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.vcard.ShareCard
import com.yasin.vcardly.presentation.contacts.ContactDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ContactQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    contacts: ContactRepository,
) : ViewModel() {
    private val contactId: Long = checkNotNull(savedStateHandle[ContactDetailViewModel.ARG_CONTACT_ID])

    /** null while loading or if the contact no longer exists. */
    val card: StateFlow<ShareCard?> = contacts.observeContact(contactId)
        .map { it?.let { d -> ShareCard.of(d.contact) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
