package com.yasin.vcardly.presentation.transfer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppDispatchers
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.TagRepository
import com.yasin.vcardly.domain.vcard.ImportCandidate
import com.yasin.vcardly.domain.vcard.ImportStatus
import com.yasin.vcardly.domain.vcard.VCardData
import com.yasin.vcardly.domain.vcard.VCardImporter
import com.yasin.vcardly.domain.vcard.VCardParser
import com.yasin.vcardly.domain.vcard.VCardPhone
import com.yasin.vcardly.domain.vcard.VCardWriter
import com.yasin.vcardly.presentation.common.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportFailure { UNREADABLE, TOO_LARGE, NO_CONTACTS }

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Preview(val entries: List<ImportCandidate>, val selected: Set<Int>) : ImportState
    data object Importing : ImportState
    data class Done(val imported: Int, val duplicates: Int, val unusable: Int) : ImportState
    data class Failed(val reason: ImportFailure) : ImportState
}

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Done(val count: Int) : ExportState
    data object Failed : ExportState
}

data class TransferUiState(val import: ImportState = ImportState.Idle, val export: ExportState = ExportState.Idle)

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactRepository,
    private val tags: TagRepository,
    private val dispatchers: AppDispatchers,
) : ViewModel() {
    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    // ---------------- import ----------------

    fun readFile(uri: Uri) {
        _state.update { it.copy(import = ImportState.Reading) }
        viewModelScope.launch {
            val text = withContext(dispatchers.io) { readText(uri) }
            val parsed = when (text) {
                is Read.Failure -> return@launch fail(text.reason)
                is Read.Text -> VCardParser.parse(text.value)
            }
            if (parsed.isEmpty()) return@launch fail(ImportFailure.NO_CONTACTS)
            val existing = contacts.observeContacts(ContactFilter()).first().map { it.contact }
            val entries = withContext(dispatchers.default) { VCardImporter.prepare(parsed, existing) }
            _state.update {
                it.copy(import = ImportState.Preview(entries, entries.filter { e -> e.status == ImportStatus.NEW }.map { e -> e.index }.toSet()))
            }
        }
    }

    fun toggle(index: Int) = _state.update { s ->
        val p = s.import as? ImportState.Preview ?: return@update s
        s.copy(import = p.copy(selected = if (index in p.selected) p.selected - index else p.selected + index))
    }

    fun importSelected() {
        val p = _state.value.import as? ImportState.Preview ?: return
        _state.update { it.copy(import = ImportState.Importing) }
        viewModelScope.launch {
            var imported = 0
            p.entries.filter { it.index in p.selected && it.status == ImportStatus.NEW && it.contact != null }.forEach { e ->
                val tagIds = e.tags.mapNotNull { tags.findOrCreate(it)?.id }.toSet()
                contacts.save(e.contact!!, tagIds)
                imported++
            }
            _state.update {
                it.copy(import = ImportState.Done(imported, p.entries.count { e -> e.status == ImportStatus.DUPLICATE }, p.entries.count { e -> e.status == ImportStatus.UNUSABLE }))
            }
        }
    }

    fun resetImport() = _state.update { it.copy(import = ImportState.Idle) }

    private fun fail(reason: ImportFailure) = _state.update { it.copy(import = ImportState.Failed(reason)) }

    private sealed interface Read {
        data class Text(val value: String) : Read
        data class Failure(val reason: ImportFailure) : Read
    }

    /** Reads at most [MAX_BYTES]; anything larger is refused rather than loaded into memory. */
    private fun readText(uri: Uri): Read = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = readUpTo(input, MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) Read.Failure(ImportFailure.TOO_LARGE) else Read.Text(String(bytes, Charsets.UTF_8))
        } ?: Read.Failure(ImportFailure.UNREADABLE)
    } catch (_: IOException) {
        Read.Failure(ImportFailure.UNREADABLE)
    } catch (_: SecurityException) {
        Read.Failure(ImportFailure.UNREADABLE)
    }

    /** InputStream.readNBytes needs API 33; the app supports 26+. Reads at most [limit] bytes. */
    private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (total < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    // ---------------- export ----------------

    /** Writes every contact (including private notes, category and tags) to the file the user chose. */
    fun exportAll(uri: Uri) {
        _state.update { it.copy(export = ExportState.Working) }
        viewModelScope.launch {
            val all = contacts.observeContacts(ContactFilter()).first()
            val cards = all.map { d ->
                val c = d.contact
                VCardData(
                    fullName = c.fullName, jobTitle = c.jobTitle, company = c.company,
                    phones = listOf(VCardPhone(c.phone, "CELL"), VCardPhone(c.phoneAlt, "WORK")).filter { it.number.isNotBlank() },
                    emails = listOf(c.email, c.emailAlt).filter { it.isNotBlank() },
                    website = c.website, address = c.address, notes = c.notes,
                    categories = listOfNotNull(d.category?.displayName()?.asString(context)) + d.tags.map { it.name },
                )
            }
            val ok = withContext(dispatchers.io) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(VCardWriter.write(cards, Instant.now()).toByteArray(Charsets.UTF_8))
                    } != null
                } catch (_: IOException) {
                    false
                }
            }
            _state.update { it.copy(export = if (ok) ExportState.Done(cards.size) else ExportState.Failed) }
        }
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
