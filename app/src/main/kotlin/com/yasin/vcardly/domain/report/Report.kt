package com.yasin.vcardly.domain.report

import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.usecase.CategoryBreakdownItem
import java.time.YearMonth

/** Time window for the "activity" part of the report. Library-wide figures (categories, tags, sources) ignore it. */
enum class ReportRange { LAST_3_MONTHS, LAST_12_MONTHS, ALL_TIME }

data class MonthCount(val month: YearMonth, val count: Int)

data class FollowUpActivity(
    /** Follow-ups whose due time has passed within the range. */
    val dueSoFar: Int,
    val completed: Int,
    /** Still pending although already due. */
    val overdue: Int,
    val byType: List<Pair<FollowUpType, Int>>,
) {
    /** completed / dueSoFar, or null when nothing was due. */
    val completionRate: Float? get() = if (dueSoFar == 0) null else completed.toFloat() / dueSoFar
}

data class Report(
    val range: ReportRange,
    val generatedAtMillis: Long,
    val totalContacts: Int,
    val favorites: Int,
    val addedInRange: Int,
    val monthly: List<MonthCount>,
    val byCategory: List<CategoryBreakdownItem>,
    val bySource: List<Pair<ContactSource, Int>>,
    val topTags: List<Pair<String, Int>>,
    val followUps: FollowUpActivity,
)
