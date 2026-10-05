package com.yasin.vcardly.presentation.followups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.reminder.DueTime
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.usecase.FollowUpField
import com.yasin.vcardly.domain.usecase.FollowUpManager
import com.yasin.vcardly.domain.usecase.FollowUpValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowUpForm(
    val contactId: Long = 0,
    val contactName: String = "",
    val title: String = "",
    val notes: String = "",
    val type: FollowUpType = FollowUpType.CALL,
    val dueAt: Long = 0,
    val reminderEnabled: Boolean = true,
    val reminderOffsetMinutes: Int = 0,
)

data class FollowUpEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val form: FollowUpForm = FollowUpForm(),
    val errors: Map<FollowUpField, Reason> = emptyMap(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FollowUpEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val followUps: FollowUpRepository,
    private val manager: FollowUpManager,
    private val contacts: ContactRepository,
    private val clock: Clock,
) : ViewModel() {
    /** 0 = new follow-up. */
    private val followUpId: Long = savedStateHandle[ARG_FOLLOW_UP_ID] ?: 0L
    /** Optional pre-selected contact for new follow-ups (0 = ask the user to pick). */
    private val presetContactId: Long = savedStateHandle[ARG_CONTACT_ID] ?: 0L

    private data class Local(
        val loaded: Boolean, val notFound: Boolean, val initial: FollowUpForm, val form: FollowUpForm,
        val errors: Map<FollowUpField, Reason>, val saving: Boolean,
    )

    private val defaultForm = FollowUpForm(dueAt = DueTime.defaultDue(clock.instant(), clock.zone))
    private val local = MutableStateFlow(
        Local(loaded = false, notFound = false, initial = defaultForm, form = defaultForm, errors = emptyMap(), saving = false),
    )
    private var original: FollowUp? = null

    private val _saved = Channel<Long>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    val uiState: StateFlow<FollowUpEditUiState> = local.map { l ->
        FollowUpEditUiState(
            isLoading = !l.loaded, isNew = followUpId == 0L, notFound = l.notFound, form = l.form,
            errors = l.errors, isSaving = l.saving, isDirty = l.form != l.initial, zone = clock.zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FollowUpEditUiState())

    // ---- contact picker ----
    private val pickerQuery = MutableStateFlow("")
    val pickerQueryText: StateFlow<String> = pickerQuery
    val pickerResults: StateFlow<List<ContactDetails>> = pickerQuery
        .flatMapLatest { contacts.observeContacts(ContactFilter(query = it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setPickerQuery(q: String) { pickerQuery.value = q }

    init {
        viewModelScope.launch {
            if (followUpId != 0L) {
                val f = followUps.get(followUpId)
                val name = f?.let { contacts.getContact(it.contactId)?.contact?.fullName }
                if (f == null || name == null) {
                    local.update { it.copy(loaded = true, notFound = true) }
                } else {
                    original = f
                    val form = FollowUpForm(f.contactId, name, f.title, f.notes, f.type, f.dueAt, f.reminderEnabled, f.reminderOffsetMinutes)
                    local.update { it.copy(loaded = true, initial = form, form = form) }
                }
            } else {
                val preset = if (presetContactId != 0L) contacts.getContact(presetContactId)?.contact else null
                val form = defaultForm.copy(contactId = preset?.id ?: 0, contactName = preset?.fullName.orEmpty())
                local.update { it.copy(loaded = true, initial = form, form = form) }
            }
        }
    }

    fun update(field: FollowUpField? = null, transform: (FollowUpForm) -> FollowUpForm) {
        local.update { l -> l.copy(form = transform(l.form), errors = if (field != null) l.errors - field else l.errors) }
    }

    fun pickContact(details: ContactDetails) = update(FollowUpField.CONTACT) {
        it.copy(contactId = details.contact.id, contactName = details.contact.fullName)
    }

    fun save() {
        val s = local.value
        if (s.saving || !s.loaded || s.notFound) return
        val f = s.form
        val base = original ?: FollowUp(contactId = f.contactId, title = f.title, dueAt = f.dueAt)
        val followUp = base.copy(
            contactId = f.contactId, title = f.title, notes = f.notes, type = f.type, dueAt = f.dueAt,
            reminderEnabled = f.reminderEnabled, reminderOffsetMinutes = f.reminderOffsetMinutes,
        )
        val errors = FollowUpValidator.validate(followUp)
        if (errors.isNotEmpty()) { local.update { it.copy(errors = errors) }; return }
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = manager.save(followUp)
            local.update { it.copy(saving = false, initial = it.form) }
            _saved.send(id)
        }
    }

    companion object {
        const val ARG_FOLLOW_UP_ID = "followUpId"
        const val ARG_CONTACT_ID = "contactId"
    }
}
