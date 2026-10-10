package com.yasin.vcardly.presentation.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlySectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyStatCard
import com.yasin.vcardly.core.designsystem.component.VCardlyTonalButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.StatValueStyle
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.report.Report
import com.yasin.vcardly.domain.report.ReportRange
import com.yasin.vcardly.presentation.common.displayName
import com.yasin.vcardly.presentation.followups.labelRes
import java.text.NumberFormat

data class ReportsActions(
    val onNavigateUp: () -> Unit = {},
    val onRange: (ReportRange) -> Unit = {},
    val onExport: (ExportFormat) -> Unit = {},
)

@Composable
fun ReportsScreen(onNavigateUp: () -> Unit, onUpgrade: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPro by viewModel.exportsUnlocked.collectAsStateWithLifecycle()

    // System file pickers: the user chooses where each file goes; the app needs no storage permission.
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let { u -> viewModel.export(ExportFormat.PDF, u) } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> viewModel.export(ExportFormat.CSV, u) } }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { it?.let { u -> viewModel.export(ExportFormat.XLSX, u) } }
    val pdfName = stringResource(R.string.export_file_pdf)
    val csvName = stringResource(R.string.export_file_csv)
    val xlsxName = stringResource(R.string.export_file_xlsx)

    ReportsContent(
        state = state,
        isPro = isPro,
        actions = ReportsActions(
            onNavigateUp = onNavigateUp,
            onRange = viewModel::setRange,
            onExport = { format ->
                when (format) {
                    ExportFormat.CSV -> csvLauncher.launch(csvName)
                    ExportFormat.PDF -> if (isPro) pdfLauncher.launch(pdfName) else onUpgrade()
                    ExportFormat.XLSX -> if (isPro) xlsxLauncher.launch(xlsxName) else onUpgrade()
                }
            },
        ),
    )
}

/** Stateless analytics content (used directly by UI tests). */
@Composable
fun ReportsContent(state: ReportsUiState, isPro: Boolean, actions: ReportsActions) {
    val report = state.report
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(
            title = stringResource(R.string.reports_screen_title),
            onNavigateUp = actions.onNavigateUp,
            actions = { RangePicker(state.range, actions.onRange) },
        )
        when {
            report == null -> VCardlyLoadingState(kind = SkeletonKind.CARDS)
            report.totalContacts == 0 && report.followUps.dueSoFar == 0 -> VCardlyEmptyState(
                icon = Icons.Rounded.BarChart,
                title = stringResource(R.string.reports_empty_title),
                message = stringResource(R.string.reports_empty_message),
                tone = MaterialTheme.vcColors.lavender,
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = MaterialTheme.spacing.screen).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Stats(report)
                CategoryCard(report)
                GrowthCard(report)
                HealthCard(report)
                SourcesCard(report)
                ExportSection(state.export, isPro, actions.onExport)
            }
        }
    }
}

@Composable
private fun RangePicker(range: ReportRange, onRange: (ReportRange) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 8.dp)) {
        VCardlyTonalButton(stringResource(range.labelRes()), onClick = { open = true }, leadingIcon = Icons.Rounded.CalendarMonth)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ReportRange.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(stringResource(r.labelRes())) },
                    onClick = { onRange(r); open = false },
                    trailingIcon = if (r == range) { { Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.common_selected), tint = MaterialTheme.colorScheme.primary) } } else null,
                )
            }
        }
    }
}

@Composable
private fun Stats(report: Report) {
    val number = remember { NumberFormat.getInstance() }
    val colors = MaterialTheme.vcColors
    val rate = report.followUps.completionRate
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VCardlyStatCard(number.format(report.totalContacts), stringResource(R.string.report_total_contacts), Icons.Rounded.PeopleAlt, colors.blue, Modifier.weight(1f))
            VCardlyStatCard(number.format(report.favorites), stringResource(R.string.dashboard_favorites), Icons.Rounded.Star, colors.rose, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VCardlyStatCard(number.format(report.addedInRange), stringResource(R.string.report_new_contacts), Icons.Rounded.PersonAddAlt1, colors.mint, Modifier.weight(1f))
            VCardlyStatCard(
                number.format(report.followUps.completed), stringResource(R.string.report_completed_follow_ups), Icons.Rounded.TaskAlt, colors.lavender, Modifier.weight(1f),
                supporting = rate?.let { stringResource(R.string.report_rate_short, NumberFormat.getPercentInstance().format(it)) },
                supportingColor = colors.lavender.content,
            )
        }
    }
}

@Composable
private fun CategoryCard(report: Report) {
    if (report.byCategory.isEmpty()) return
    val uncategorised = stringResource(R.string.category_uncategorised)
    val other = MaterialTheme.colorScheme.outline
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Text(stringResource(R.string.report_contacts_by_category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
        // Colour follows the category (its own stored colour), never its rank.
        DonutChart(
            segments = report.byCategory.map { Segment(it.category?.displayName()?.asString() ?: uncategorised, it.count, it.category?.let { c -> Color(c.colorArgb) } ?: other) },
            centerLabel = stringResource(R.string.report_total_short),
        )
    }
}

@Composable
private fun GrowthCard(report: Report) {
    val number = remember { NumberFormat.getInstance() }
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.report_contact_growth), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.report_added_per_month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.report_plus_count, number.format(report.addedInRange)), style = StatValueStyle, color = MaterialTheme.vcColors.blue.content)
        }
        GrowthChart(report.monthly, MaterialTheme.vcColors.blue.accent, Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun HealthCard(report: Report) {
    val f = report.followUps
    if (f.dueSoFar == 0 && f.byType.isEmpty()) return
    val colors = MaterialTheme.vcColors
    val percent = remember { NumberFormat.getPercentInstance() }
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.report_follow_up_health), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.report_follow_up_health_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            f.completionRate?.let { Text(percent.format(it), style = StatValueStyle, color = colors.mint.content) }
        }
        // Status colours, always with labels: done (good) and still open past due (critical).
        StackedBar(
            listOf(
                Segment(stringResource(R.string.followup_completed), f.completed, colors.mint.accent),
                Segment(stringResource(R.string.followup_overdue), f.overdue, colors.rose.accent),
            ),
            Modifier.padding(top = 16.dp),
        )
        if (f.byType.isNotEmpty()) {
            Text(stringResource(R.string.report_followups_by_type), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            HorizontalBars(f.byType.map { BarItem(stringResource(it.first.labelRes()), it.second) }, colors.lavender.accent)
        }
    }
}

@Composable
private fun SourcesCard(report: Report) {
    if (report.bySource.isEmpty() && report.topTags.isEmpty()) return
    val colors = MaterialTheme.vcColors
    VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        if (report.bySource.isNotEmpty()) {
            Text(stringResource(R.string.report_by_source), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            HorizontalBars(report.bySource.map { BarItem(sourceLabel(it.first), it.second) }, colors.blue.accent)
        }
        if (report.topTags.isNotEmpty()) {
            Text(stringResource(R.string.report_top_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
            HorizontalBars(report.topTags.map { BarItem("#${it.first}", it.second) }, colors.orange.accent)
        }
    }
}

@Composable
private fun ExportSection(status: ExportStatus, isPro: Boolean, onExport: (ExportFormat) -> Unit) {
    val colors = MaterialTheme.vcColors
    val busy = status == ExportStatus.Working
    VCardlySectionHeader(stringResource(R.string.reports_export))
    Text(stringResource(R.string.reports_export_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ExportTile(stringResource(R.string.export_tile_pdf), Icons.Rounded.PictureAsPdf, colors.rose, locked = !isPro, enabled = !busy, Modifier.weight(1f)) { onExport(ExportFormat.PDF) }
        ExportTile(stringResource(R.string.export_tile_csv), Icons.Rounded.Description, colors.blue, locked = false, enabled = !busy, Modifier.weight(1f)) { onExport(ExportFormat.CSV) }
        ExportTile(stringResource(R.string.export_tile_xlsx), Icons.Rounded.TableChart, colors.mint, locked = !isPro, enabled = !busy, Modifier.weight(1f)) { onExport(ExportFormat.XLSX) }
    }
    Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (status) {
            ExportStatus.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.export_working), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
            }
            is ExportStatus.Done -> VCardlyNotice(stringResource(R.string.export_done), colors.mint, Icons.Rounded.CheckCircle)
            ExportStatus.Failed -> VCardlyNotice(stringResource(R.string.export_failed_detail), colors.rose, Icons.Rounded.Description)
            ExportStatus.Idle -> Unit
        }
    }
    Text(stringResource(R.string.export_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Export format tile. Pro-only formats say so in text ("PDF · Pro") and show a lock, not colour alone. */
@Composable
private fun ExportTile(label: String, icon: ImageVector, tone: com.yasin.vcardly.core.designsystem.theme.Tone, locked: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val text = if (locked) stringResource(R.string.pro_locked_label, label) else label
    VCardlyCard(modifier, onClick = if (enabled) onClick else null, contentPadding = 14.dp) {
        Box {
            IconBadge(icon, tone, size = 44.dp, iconSize = 22.dp)
            if (locked) {
                Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp), maxLines = 1)
    }
}

private fun ReportRange.labelRes(): Int = when (this) {
    ReportRange.LAST_3_MONTHS -> R.string.report_range_3_months
    ReportRange.LAST_12_MONTHS -> R.string.report_range_12_months
    ReportRange.ALL_TIME -> R.string.report_range_all
}

@Composable
private fun sourceLabel(source: ContactSource): String = stringResource(
    when (source) {
        ContactSource.MANUAL -> R.string.source_manual
        ContactSource.SCAN -> R.string.source_scan
        ContactSource.IMPORT -> R.string.source_import
    },
)
