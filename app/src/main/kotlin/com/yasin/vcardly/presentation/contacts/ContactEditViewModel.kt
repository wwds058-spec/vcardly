package com.yasin.vcardly.presentation.contacts

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppError.Validation.Reason
import com.yasin.vcardly.core.image.CardImageRef
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.Tag
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.TagRepository
import com.yasin.vcardly.domain.usecase.ContactField
import com.yasin.vcardly.domain.usecase.ContactValidator
import com.yasin.vcardly.presentation.scan.ScanDraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
    val frontImage: CardImageRef? = null,
    val backImage: CardImageRef? = null,
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
    val saveFailed: Boolean = false,
    val isDirty: Boolean = false,
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** True when the form was pre-filled from OCR: the UI must ask the user to review every field. */
    val isFromScan: Boolean = false,
    val ocrFailed: Boolean = false,
    val unmatchedLines: List<String> = emptyList(),
    /** How many fields text recognition filled in (shown as "We found N details"). */
    val detectedCount: Int = 0,
    /** True while an image is being imported or rotated. */
    val imageBusy: Boolean = false,
    val imageFailed: Boolean = false,
)

@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contacts: ContactRepository,
    private val tagRepository: TagRepository,
    private val imageStore: CardImageStore,
    private val draftStore: ScanDraftStore,
    categories: CategoryRepository,
) : ViewModel() {
    /** 0 means "new contact". */
    private val contactId: Long = savedStateHandle[ContactDetailViewModel.ARG_CONTACT_ID] ?: 0L
    private val fromScan: Boolean = contactId == 0L && (savedStateHandle[ARG_FROM_SCAN] ?: false)

    private data class Local(
        val loaded: Boolean,
        val notFound: Boolean,
        val initial: ContactForm,
        val form: ContactForm,
        val errors: Map<ContactField, Reason>,
        val saving: Boolean,
        val saveFailed: Boolean,
        val unmatched: List<String>,
        val ocrFailed: Boolean,
        val detected: Int = 0,
        val imageBusy: Boolean = false,
        val imageFailed: Boolean = false,
    )

    private val local = MutableStateFlow(
        Local(
            loaded = contactId == 0L, notFound = false, initial = ContactForm(), form = ContactForm(),
            errors = emptyMap(), saving = false, saveFailed = false, unmatched = emptyList(), ocrFailed = false,
        ),
    )

    /** The contact being edited, kept so fields the form does not show (source, dates, favorite) survive a save. */
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
            saveFailed = l.saveFailed,
            isDirty = l.form != l.initial,
            categories = cats,
            tags = tags,
            isFromScan = fromScan,
            ocrFailed = l.ocrFailed,
            unmatchedLines = l.unmatched,
            detectedCount = l.detected,
            imageBusy = l.imageBusy,
            imageFailed = l.imageFailed,
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
        } else if (fromScan) {
            draftStore.peek()?.let { draft ->
                // initial stays empty so a scanned draft always counts as unsaved work.
                val f = draft.form
                val detected = listOf(f.fullName, f.jobTitle, f.company, f.phone, f.phoneAlt, f.email, f.emailAlt, f.website, f.address)
                    .count { it.isNotBlank() }
                local.update { it.copy(form = draft.form, unmatched = draft.unmatched, ocrFailed = draft.ocrFailed, detected = detected) }
            }
        }
    }

    fun update(field: ContactField? = null, transform: (ContactForm) -> ContactForm) {
        local.update { l ->
            l.copy(form = transform(l.form), errors = if (field != null) l.errors - field else l.errors)
        }
    }

    fun toggleTag(id: Long) = update { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    fun removeImage(front: Boolean) = update { if (front) it.copy(frontImage = null) else it.copy(backImage = null) }

    /** Files this form created in the scan cache; deleted when the form closes (saved copies live in permanent storage). */
    private val ownPending = mutableListOf<File>()

    /** A cache file the camera app writes the photo into (shared through FileProvider for this one capture). */
    fun newCameraFile(): File = imageStore.newCaptureFile().also { ownPending += it }

    fun onCameraResult(front: Boolean, file: File, success: Boolean) {
        if (success && file.length() > 0) setImage(front, CardImageRef.Pending(file.path))
        else file.delete()
    }

    fun onImagePicked(front: Boolean, uri: Uri) {
        viewModelScope.launch {
            local.update { it.copy(imageBusy = true, imageFailed = false) }
            val file = imageStore.importFromUri(uri)
            if (file == null) {
                local.update { it.copy(imageBusy = false, imageFailed = true) }
            } else {
                ownPending += file
                local.update { it.copy(imageBusy = false) }
                setImage(front, CardImageRef.Pending(file.path))
            }
        }
    }

    /** Rotates an image 90 degrees clockwise into a new cache file; the stored original is untouched until save. */
    fun rotateImage(front: Boolean) {
        val ref = (if (front) local.value.form.frontImage else local.value.form.backImage) ?: return
        if (local.value.imageBusy) return
        viewModelScope.launch {
            local.update { it.copy(imageBusy = true, imageFailed = false) }
            val rotated = imageStore.load(ref, ROTATE_MAX)
                ?.let { bmp -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.yasin.vcardly.core.image.BitmapOps.rotate(bmp, 90) } }
                ?.let { imageStore.saveToScanCache(it) }
            if (rotated == null) {
                local.update { it.copy(imageBusy = false, imageFailed = true) }
            } else {
                ownPending += rotated
                local.update { it.copy(imageBusy = false) }
                setImage(front, CardImageRef.Pending(rotated.path))
            }
        }
    }

    private fun setImage(front: Boolean, ref: CardImageRef) = update { if (front) it.copy(frontImage = ref) else it.copy(backImage = ref) }

    fun dismissImageError() = local.update { it.copy(imageFailed = false) }

    /** Validates only [fields] (one step of the form). Returns true when they are all valid. */
    fun validateFields(fields: Set<ContactField>): Boolean {
        val all = ContactValidator.validate(local.value.form.toContact(original, null, null))
        val stepErrors = all.filterKeys { it in fields }
        local.update { it.copy(errors = it.errors - fields + stepErrors) }
        return stepErrors.isEmpty()
    }

    override fun onCleared() {
        ownPending.forEach { it.delete() }
    }

    /** Moves a recognised-but-unclaimed line into the notes field. */
    fun addLineToNotes(line: String) {
        local.update { l ->
            val notes = if (l.form.notes.isBlank()) line else l.form.notes.trimEnd() + "\n" + line
            l.copy(form = l.form.copy(notes = notes), unmatched = l.unmatched - line)
        }
    }

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
        val form = state.form
        val validated = form.toContact(original, frontPath = null, backPath = null)
        val errors = ContactValidator.validate(validated)
        if (errors.isNotEmpty()) {
            local.update { it.copy(errors = errors, saveFailed = false) }
            return
        }
        local.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            val persistedNow = mutableListOf<String>()
            suspend fun resolve(ref: CardImageRef?): Result<String?> = when (ref) {
                null -> Result.success(null)
                is CardImageRef.Stored -> Result.success(ref.path)
                is CardImageRef.Pending -> imageStore.persist(File(ref.path))
                    ?.also { persistedNow += it }
                    ?.let { Result.success(it) }
                    ?: Result.failure(java.io.IOException())
            }
            val front = resolve(form.frontImage)
            val back = resolve(form.backImage)
            if (front.isFailure || back.isFailure) {
                persistedNow.forEach { imageStore.delete(it) }
                local.update { it.copy(saving = false, saveFailed = true) }
                return@launch
            }
            val contact = form.toContact(original, front.getOrNull(), back.getOrNull())
                .let { if (fromScan) it.copy(source = ContactSource.SCAN) else it }
            val id = contacts.save(contact, form.tagIds)

            // Images the user removed or replaced are no longer referenced.
            original?.let { o ->
                if (o.frontImagePath != contact.frontImagePath) imageStore.delete(o.frontImagePath)
                if (o.backImagePath != contact.backImagePath) imageStore.delete(o.backImagePath)
            }
            if (fromScan) {
                imageStore.clearScanCache()
                draftStore.clear()
            }
            local.update { it.copy(saving = false, initial = it.form) }
            _saved.send(Saved(id = id, wasNew = contactId == 0L))
        }
    }

    companion object {
        const val ARG_FROM_SCAN = "fromScan"
        private const val ROTATE_MAX = 3000
    }
}

private fun ContactDetails.toForm() = ContactForm(
    fullName = contact.fullName, jobTitle = contact.jobTitle, company = contact.company,
    phone = contact.phone, phoneAlt = contact.phoneAlt, email = contact.email, emailAlt = contact.emailAlt,
    website = contact.website, address = contact.address, notes = contact.notes,
    categoryId = contact.categoryId, tagIds = tags.map { it.id }.toSet(),
    frontImage = contact.frontImagePath?.let { CardImageRef.Stored(it) },
    backImage = contact.backImagePath?.let { CardImageRef.Stored(it) },
)

private fun ContactForm.toContact(original: Contact?, frontPath: String?, backPath: String?): Contact =
    (original ?: Contact(fullName = "")).copy(
        fullName = fullName, jobTitle = jobTitle, company = company,
        phone = phone, phoneAlt = phoneAlt, email = email, emailAlt = emailAlt,
        website = website, address = address, notes = notes, categoryId = categoryId,
        frontImagePath = frontPath, backImagePath = backPath,
    )
