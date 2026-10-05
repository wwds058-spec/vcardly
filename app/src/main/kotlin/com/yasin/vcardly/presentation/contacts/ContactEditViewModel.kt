package com.yasin.vcardly.presentation.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.Tag
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.TagRepository
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.domain.usecase.ContactValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable text/selection state of the form. */
data class ContactForm(
    val fullName: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val phoneAlt: String = "",
    val email: String = "",
    val emailAlt: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val categoryId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
)

/** One-shot result of a successful save. */
data class Saved(val id: Long, val wasNew: Boolean)

data class ContactEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val form: ContactForm = ContactForm(),
    val errors: Map<ContactField, Reason> = emptyMap(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contacts: ContactRepository,
    private val tagRepository: TagRepository,
    categories: CategoryRepository,
) : ViewModel() {
    /** 0 means "new contact". */
    private val contactId: Long = savedStateHandle[ContactDetailViewModel.ARG_CONTACT_ID] ?: 0L

    private data class Local(
        val loaded: Boolean,
        val notFound: Boolean,
        val initial: ContactForm,
        val form: ContactForm,
        val errors: Map<ContactField, Reason>,
        val saving: Boolean,
    )

    private val local = MutableStateFlow(
        Local(loaded = contactId == 0L, notFound = false, initial = ContactForm(), form = ContactForm(), errors = emptyMap(), saving = false),
    )

    /** The contact being edited, kept so fields the form does not show (images, source, dates, favorite) survive a save. */
    private var original: Contact? = null

    private val _saved = Channel<Saved>(Channel.BUFFERED)
    /** Emits once after a successful save; the screen navigates in response. */
    val saved = _saved.receiveAsFlow()

    val uiState: StateFlow<ContactEditUiState> = combine(
        local,
        categories.observeAll(),
        tagRepository.observeAll().map { rows -> rows.map { it.tag } },
    ) { l, cats, tags ->
        ContactEditUiState(
            isLoading = !l.loaded,
            isNew = contactId == 0L,
            notFound = l.notFound,
            form = l.form,
            errors = l.errors,
            isSaving = l.saving,
            isDirty = l.form != l.initial,
            categories = cats,
            tags = tags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactEditUiState())

    init {
        if (contactId != 0L) {
            viewModelScope.launch {
                val details = contacts.getContact(contactId)
                if (details == null) {
                    local.update { it.copy(loaded = true, notFound = true) }
                } else {
                    original = details.contact
                    val form = details.toForm()
                    local.update { it.copy(loaded = true, initial = form, form = form) }
                }
            }
        }
    }

    fun update(field: ContactField? = null, transform: (ContactForm) -> ContactForm) {
        local.update { l ->
            l.copy(form = transform(l.form), errors = if (field != null) l.errors - field else l.errors)
        }
    }

    fun toggleTag(id: Long) = update { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    /** Creates the tag if needed and selects it. Returns false for blank input. */
    fun addTag(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val tag = tagRepository.findOrCreate(name)
            if (tag != null) update { it.copy(tagIds = it.tagIds + tag.id) }
            onResult(tag != null)
        }
    }

    fun save() {
        val state = local.value
        if (state.saving || !state.loaded || state.notFound) return
        val contact = state.form.toContact(original)
        val errors = ContactValidator.validate(contact)
        if (errors.isNotEmpty()) {
            local.update { it.copy(errors = errors) }
            return
        }
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = contacts.save(contact, state.form.tagIds)
            local.update { it.copy(saving = false, initial = it.form) }
            _saved.send(Saved(id = id, wasNew = contactId == 0L))
        }
    }
}

private fun com.yasin.vcardly.domain.model.ContactDetails.toForm() = ContactForm(
    fullName = contact.fullName, jobTitle = contact.jobTitle, company = contact.company,
    phone = contact.phone, phoneAlt = contact.phoneAlt, email = contact.email, emailAlt = contact.emailAlt,
    website = contact.website, address = contact.address, notes = contact.notes,
    categoryId = contact.categoryId, tagIds = tags.map { it.id }.toSet(),
)

private fun ContactForm.toContact(original: Contact?): Contact =
    (original ?: Contact(fullName = "")).copy(
        fullName = fullName, jobTitle = jobTitle, company = company,
        phone = phone, phoneAlt = phoneAlt, email = email, emailAlt = emailAlt,
        website = website, address = address, notes = notes, categoryId = categoryId,
    )
