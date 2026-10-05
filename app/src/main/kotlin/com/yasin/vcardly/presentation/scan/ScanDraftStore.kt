package com.yasin.vcardly.presentation.scan

import com.yasin.vcardly.presentation.contacts.ContactForm
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hand-off from the scan flow to the review form. Lives only in memory (it holds PII), so it is
 * intentionally lost on process death; the scan cache it points at is also a cache directory.
 */
data class ScanDraft(
    val form: ContactForm,
    val front: File?,
    val back: File?,
    /** OCR lines no rule claimed; shown to the user, never saved automatically. */
    val unmatched: List<String>,
    /** True when text recognition failed or found nothing, so the form is empty rather than "wrong". */
    val ocrFailed: Boolean,
)

@Singleton
class ScanDraftStore @Inject constructor() {
    @Volatile private var draft: ScanDraft? = null
    fun set(value: ScanDraft) { draft = value }
    fun peek(): ScanDraft? = draft
    fun clear() { draft = null }
}
