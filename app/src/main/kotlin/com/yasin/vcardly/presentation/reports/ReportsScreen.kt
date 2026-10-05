package com.yasin.vcardly.presentation.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.StatCard
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.report.Report
import com.yasin.vcardly.domain.report.ReportRange
import com.yasin.vcardly.presentation.common.displayName
import com.yasin.vcardly.presentation.followups.labelRes
import java.text.NumberFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(onNavigateUp: () -> Unit, onUpgrade: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()

    // System file pickers: the user chooses where each file goes; the app needs no storage permission.
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let { u -> viewModel.export(ExportFormat.PDF, u) } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> viewModel.export(ExportFormat.CSV, u) } }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { it?.let { u -> viewModel.export(ExportFormat.XLSX, u) } }
    val pdfName = stringResource(R.string.export_file_pdf)
    val csvName = stringResource(R.string.export_file_csv)
    val xlsxName = stringResource(R.string.export_file_xlsx)
    val number = NumberFormat.getInstance()

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.reports_title), onNavigateUp = onNavigateUp)
        when {
            report == null -> VCardlyLoadingState()
            report.totalContacts == 0 && report.followUps.dueSoFar == 0 -> VCardlyEmptyState(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.reports_empty_title),
                message = stringResource(R.string.reports_empty_message),
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    ReportRange.entries.forEach { r ->
                        FilterChip(selected = r == state.range, onClick = { viewModel.setRange(r) }, label = { Text(stringResource(r.labelRes())) })
                    }
                }

                SectionHeader(stringResource(R.string.report_section_activity))
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    StatCard(stringResource(R.string.report_added_in_range), number.format(report.addedInRange), Modifier.weight(1f))
                    StatCard(stringResource(R.string.followup_completed), number.format(report.followUps.completed), Modifier.weight(1f))
                    StatCard(stringResource(R.string.followup_overdue), number.format(report.followUps.overdue), Modifier.weight(1f))
                }
                Text(stringResource(R.string.report_added_per_month), style = MaterialTheme.typography.titleMedium)
                MonthlyBarChart(report.monthly)

                report.followUps.completionRate?.let { rate ->
                    ProportionBar(
                        fraction = rate,
                        label = stringResource(R.string.report_completion_summary, number.format((rate * 100).toInt()), number.format(report.followUps.completed), number.format(report.followUps.dueSoFar)),
                        modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
                    )
                }
                if (report.followUps.byType.isNotEmpty()) {
                    Text(stringResource(R.string.report_followups_by_type), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = MaterialTheme.spacing.sm))
                    HorizontalBars(report.followUps.byType.map { BarItem(typeLabel(it.first), it.second) })
                }

                SectionHeader(stringResource(R.string.report_section_library))
                Text(stringResource(R.string.report_library_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    StatCard(stringResource(R.string.dashboard_total), number.format(report.totalContacts), Modifier.weight(1f))
                    StatCard(stringResource(R.string.dashboard_favorites), number.format(report.favorites), Modifier.weight(1f))
                }
                LibraryCharts(report)

                SectionHeader(stringResource(R.string.reports_export))
                Text(stringResource(R.string.reports_export_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val busy = state.export == ExportStatus.Working
                VCardlySecondaryButton(proLabel(stringResource(R.string.export_pdf), isPro), onClick = { if (isPro) pdfLauncher.launch(pdfName) else onUpgrade() }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                VCardlySecondaryButton(stringResource(R.string.export_csv), onClick = { csvLauncher.launch(csvName) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                VCardlySecondaryButton(proLabel(stringResource(R.string.export_xlsx), isPro), onClick = { if (isPro) xlsxLauncher.launch(xlsxName) else onUpgrade() }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                when (val e = state.export) {
                    ExportStatus.Working -> CircularProgressIndicator()
                    is ExportStatus.Done -> Text(stringResource(R.string.export_done), color = MaterialTheme.colorScheme.primary)
                    ExportStatus.Failed -> Text(stringResource(R.string.export_failed), color = MaterialTheme.colorScheme.error)
                    ExportStatus.Idle -> Unit
                }
                Text(stringResource(R.string.export_privacy_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LibraryCharts(report: Report) {
    val uncategorised = stringResource(R.string.category_uncategorised)
    if (report.byCategory.isNotEmpty()) {
        Text(stringResource(R.string.dashboard_section_categories), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = MaterialTheme.spacing.sm))
        HorizontalBars(report.byCategory.map { BarItem(it.category?.displayName()?.asString() ?: uncategorised, it.count) })
    }
    if (report.bySource.isNotEmpty()) {
        Text(stringResource(R.string.report_by_source), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = MaterialTheme.spacing.sm))
        HorizontalBars(report.bySource.map { BarItem(sourceLabel(it.first), it.second) })
    }
    if (report.topTags.isNotEmpty()) {
        Text(stringResource(R.string.report_top_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = MaterialTheme.spacing.sm))
        HorizontalBars(report.topTags.map { BarItem("#${it.first}", it.second) })
    }
}

private fun ReportRange.labelRes(): Int = when (this) {
    ReportRange.LAST_3_MONTHS -> R.string.report_range_3_months
    ReportRange.LAST_12_MONTHS -> R.string.report_range_12_months
    ReportRange.ALL_TIME -> R.string.report_range_all
}

@Composable
private fun typeLabel(type: com.yasin.vcardly.domain.model.FollowUpType): String = stringResource(type.labelRes())

@Composable
private fun sourceLabel(source: com.yasin.vcardly.domain.model.ContactSource): String = stringResource(
    when (source) {
        com.yasin.vcardly.domain.model.ContactSource.MANUAL -> R.string.source_manual
        com.yasin.vcardly.domain.model.ContactSource.SCAN -> R.string.source_scan
        com.yasin.vcardly.domain.model.ContactSource.IMPORT -> R.string.source_import
    },
)

/** "PDF report · Pro" for free users, so the lock is visible before tapping (as text, not only as an icon or colour). */
@Composable
private fun proLabel(label: String, isPro: Boolean): String = if (isPro) label else stringResource(R.string.pro_locked_label, label)
