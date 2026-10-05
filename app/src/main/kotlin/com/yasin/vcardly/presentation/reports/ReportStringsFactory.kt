package com.yasin.vcardly.presentation.reports

import android.content.Context
import com.yasin.vcardly.R
import com.yasin.vcardly.core.export.ExportLabels
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.report.ReportRange
import com.yasin.vcardly.domain.report.ReportStrings
import com.yasin.vcardly.presentation.followups.labelRes
import java.text.DateFormat
import java.util.Date

/** Resolves every user-visible word in exports from string resources, in the current app language. */
object ReportStringsFactory {
    fun sourceName(c: Context, s: ContactSource) = c.getString(
        when (s) {
            ContactSource.MANUAL -> R.string.source_manual
            ContactSource.SCAN -> R.string.source_scan
            ContactSource.IMPORT -> R.string.source_import
        },
    )

    fun typeName(c: Context, t: FollowUpType) = c.getString(t.labelRes())

    fun rangeName(c: Context, r: ReportRange) = c.getString(
        when (r) {
            ReportRange.LAST_3_MONTHS -> R.string.report_range_3_months
            ReportRange.LAST_12_MONTHS -> R.string.report_range_12_months
            ReportRange.ALL_TIME -> R.string.report_range_all
        },
    )

    fun exportLabels(c: Context) = ExportLabels(
        contactsSheet = c.getString(R.string.export_sheet_contacts),
        followUpsSheet = c.getString(R.string.export_sheet_follow_ups),
        contactHeaders = listOf(
            R.string.field_full_name, R.string.field_job_title, R.string.field_company, R.string.field_phone, R.string.field_phone_alt,
            R.string.field_email, R.string.field_email_alt, R.string.field_website, R.string.field_address, R.string.field_category,
            R.string.field_tags, R.string.export_col_favorite, R.string.export_col_source, R.string.field_notes, R.string.export_col_added,
        ).map(c::getString),
        followUpHeaders = listOf(
            R.string.field_full_name, R.string.field_company, R.string.followup_title_label, R.string.followup_type,
            R.string.export_col_status, R.string.followup_due, R.string.export_col_completed, R.string.field_notes,
        ).map(c::getString),
        yes = c.getString(R.string.common_yes),
        no = c.getString(R.string.common_no),
        source = ContactSource.entries.associateWith { sourceName(c, it) },
        type = FollowUpType.entries.associateWith { typeName(c, it) },
        status = mapOf(
            FollowUpStatus.PENDING to c.getString(R.string.export_status_pending),
            FollowUpStatus.COMPLETED to c.getString(R.string.followup_completed),
        ),
    )

    fun reportStrings(c: Context, range: ReportRange, generatedAt: Long) = ReportStrings(
        title = c.getString(R.string.report_pdf_title),
        generatedOn = c.getString(R.string.report_generated_on, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(generatedAt))),
        rangeLabel = rangeName(c, range),
        summary = c.getString(R.string.report_section_summary),
        totalContacts = c.getString(R.string.dashboard_total),
        favorites = c.getString(R.string.dashboard_favorites),
        addedInRange = c.getString(R.string.report_added_in_range),
        addedPerMonth = c.getString(R.string.report_added_per_month),
        followUps = c.getString(R.string.dashboard_section_follow_ups),
        followUpsDue = c.getString(R.string.report_followups_due),
        followUpsCompleted = c.getString(R.string.followup_completed),
        followUpsOverdue = c.getString(R.string.followup_overdue),
        completionRate = c.getString(R.string.report_completion_rate),
        followUpsByType = c.getString(R.string.report_followups_by_type),
        byCategory = c.getString(R.string.dashboard_section_categories),
        uncategorised = c.getString(R.string.category_uncategorised),
        bySource = c.getString(R.string.report_by_source),
        topTags = c.getString(R.string.report_top_tags),
        directory = c.getString(R.string.report_directory),
        directoryHeader = listOf(R.string.field_full_name, R.string.field_company, R.string.field_phone, R.string.field_email).map(c::getString),
        none = c.getString(R.string.report_none),
    )
}
