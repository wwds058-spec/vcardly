package com.yasin.vcardly.presentation.reports

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.common.AppDispatchers
import com.yasin.vcardly.core.export.CsvWriter
import com.yasin.vcardly.core.export.ExportTables
import com.yasin.vcardly.core.export.ExportWriter
import com.yasin.vcardly.core.export.PdfDocRenderer
import com.yasin.vcardly.core.export.XlsxWriter
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.report.Report
import com.yasin.vcardly.domain.report.ReportBuilder
import com.yasin.vcardly.domain.report.ReportPdfContent
import com.yasin.vcardly.domain.report.ReportRange
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.presentation.common.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportFormat { PDF, CSV, XLSX }

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Working : ExportStatus
    data class Done(val format: ExportFormat) : ExportStatus
    data object Failed : ExportStatus
}

data class ReportsUiState(
    val range: ReportRange = ReportRange.LAST_12_MONTHS,
    /** null while the first report is being computed. */
    val report: Report? = null,
    val export: ExportStatus = ExportStatus.Idle,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactRepository,
    private val followUps: FollowUpRepository,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val writer: ExportWriter,
) : ViewModel() {
    private val range = MutableStateFlow(ReportRange.LAST_12_MONTHS)
    private val export = MutableStateFlow<ExportStatus>(ExportStatus.Idle)

    private val report = combine(range, contacts.observeContacts(ContactFilter()), followUps.observeAll()) { r, c, f ->
        ReportBuilder.build(c, f.map { it.followUp }, r, clock.instant(), clock.zone)
    }.flowOn(dispatchers.default)

    val uiState: StateFlow<ReportsUiState> = combine(range, report, export) { r, rep, e -> ReportsUiState(r, rep, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun setRange(r: ReportRange) { range.value = r }

    /** Writes [format] to the file the user chose. Exports always cover the whole library, plus the selected range for PDF. */
    fun export(format: ExportFormat, uri: Uri) {
        export.value = ExportStatus.Working
        viewModelScope.launch {
            val allContacts = contacts.observeContacts(ContactFilter()).first()
            val allFollowUps = followUps.observeAll().first()
            val labels = ReportStringsFactory.exportLabels(context)
            val ok = withContext(dispatchers.default) {
                val zone = clock.zone
                val categoryName: (com.yasin.vcardly.domain.model.Category) -> String = { it.displayName().asString(context) }
                val contactsTable = ExportTables.contacts(allContacts, categoryName, labels, zone)
                when (format) {
                    ExportFormat.CSV -> writer.write(uri) { out ->
                        out.write((CsvWriter.BOM + CsvWriter.write(contactsTable)).toByteArray(Charsets.UTF_8))
                    }
                    ExportFormat.XLSX -> writer.write(uri) { out ->
                        XlsxWriter.write(listOf(contactsTable, ExportTables.followUps(allFollowUps, labels, zone)), out)
                    }
                    ExportFormat.PDF -> {
                        val now = clock.instant()
                        val rep = ReportBuilder.build(allContacts, allFollowUps.map { it.followUp }, range.value, now, zone)
                        val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
                        val doc = ReportPdfContent.build(
                            report = rep,
                            strings = ReportStringsFactory.reportStrings(context, range.value, now.toEpochMilli()),
                            categoryName = { it?.displayName()?.asString(context).orEmpty() },
                            sourceName = { ReportStringsFactory.sourceName(context, it) },
                            typeName = { ReportStringsFactory.typeName(context, it) },
                            monthLabel = { monthFmt.format(it) },
                            directoryRows = allContacts.map { listOf(it.contact.fullName, it.contact.company, it.contact.phone, it.contact.email) },
                            number = NumberFormat.getInstance(),
                        )
                        val rtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL
                        writer.write(uri) { out -> PdfDocRenderer.render(doc, out, rtl) }
                    }
                }
            }
            export.update { if (ok) ExportStatus.Done(format) else ExportStatus.Failed }
        }
    }
}
