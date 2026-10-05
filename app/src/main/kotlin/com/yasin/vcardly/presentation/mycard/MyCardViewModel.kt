package com.yasin.vcardly.presentation.mycard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.MyCard
import com.yasin.vcardly.domain.repository.MyCardRepository
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.domain.usecase.ContactValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null = still loading. */
@HiltViewModel
class MyCardViewModel @Inject constructor(repository: MyCardRepository) : ViewModel() {
    val card: StateFlow<MyCard?> = repository.card.map<MyCard, MyCard?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class MyCardEditUiState(
    val isLoading: Boolean = true,
    val card: MyCard = MyCard(),
    val errors: Map<ContactField, Reason> = emptyMap(),
    val isDirty: Boolean = false,
)

@HiltViewModel
class MyCardEditViewModel @Inject constructor(
    private val repository: MyCardRepository,
) : ViewModel() {
    private data class Local(val loaded: Boolean, val initial: MyCard, val card: MyCard, val errors: Map<ContactField, Reason>)

    private val local = MutableStateFlow(Local(false, MyCard(), MyCard(), emptyMap()))
    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    val uiState: StateFlow<MyCardEditUiState> = local.map {
        MyCardEditUiState(isLoading = !it.loaded, card = it.card, errors = it.errors, isDirty = it.card != it.initial)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyCardEditUiState())

    init {
        viewModelScope.launch {
            val current = repository.card.first()
            local.update { it.copy(loaded = true, initial = current, card = current) }
        }
    }

    fun update(field: ContactField, transform: (MyCard) -> MyCard) {
        local.update { it.copy(card = transform(it.card), errors = it.errors - field) }
    }

    fun save() {
        val card = local.value.card
        // The same rules as contacts apply, so a shared card always scans/dials/mails correctly.
        val errors = ContactValidator.validate(
            Contact(
                fullName = card.fullName, jobTitle = card.jobTitle, company = card.company, phone = card.phone,
                phoneAlt = card.phoneAlt, email = card.email, emailAlt = card.emailAlt, website = card.website, address = card.address,
            ),
        )
        if (errors.isNotEmpty()) { local.update { it.copy(errors = errors) }; return }
        viewModelScope.launch {
            repository.save(card)
            local.update { it.copy(initial = it.card) }
            _saved.send(Unit)
        }
    }
}
