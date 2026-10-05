package com.yasin.vcardly.domain.report

import com.yasin.vcardly.domain.model.CategoryCount
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.usecase.buildCategoryBreakdown
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Pure aggregation of the real data into a [Report]. Months are calendar months in the device's zone. */
object ReportBuilder {
    const val MAX_MONTHS = 36
    const val MAX_TAGS = 8

    fun build(
        contacts: List<ContactDetails>,
        followUps: List<FollowUp>,
        range: ReportRange,
        now: Instant,
        zone: ZoneId,
    ): Report {
        val nowMillis = now.toEpochMilli()
        val currentMonth = YearMonth.from(now.atZone(zone))
        fun monthOf(millis: Long) = YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

        val months: List<YearMonth> = when (range) {
            ReportRange.LAST_3_MONTHS -> monthsBack(currentMonth, 3)
            ReportRange.LAST_12_MONTHS -> monthsBack(currentMonth, 12)
            ReportRange.ALL_TIME -> {
                val earliest = contacts.minOfOrNull { it.contact.createdAt }?.let(::monthOf) ?: currentMonth
                val span = (java.time.temporal.ChronoUnit.MONTHS.between(earliest, currentMonth) + 1).toInt().coerceIn(1, MAX_MONTHS)
                monthsBack(currentMonth, span)
            }
        }
        val rangeStart: Long =
            if (range == ReportRange.ALL_TIME) Long.MIN_VALUE else months.first().atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val perMonth = contacts.groupingBy { monthOf(it.contact.createdAt) }.eachCount()
        val monthly = months.map { MonthCount(it, perMonth[it] ?: 0) }

        val categoryCounts = contacts.groupingBy { it.contact.categoryId }.eachCount().map { CategoryCount(it.key, it.value) }
        val categories = contacts.mapNotNull { it.category }.distinctBy { it.id }

        val topTags = contacts.flatMap { d -> d.tags.map { it.name } }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .take(MAX_TAGS).map { it.key to it.value }

        // Cancelled follow-ups were never going to be done, so they do not count as due.
        val due = followUps.filter { it.dueAt in rangeStart..nowMillis && it.status != FollowUpStatus.CANCELLED }
        val activity = FollowUpActivity(
            dueSoFar = due.size,
            completed = due.count { it.status == FollowUpStatus.COMPLETED },
            overdue = due.count { it.status.isActive },
            byType = due.groupingBy { it.type }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value },
        )

        return Report(
            range = range,
            generatedAtMillis = nowMillis,
            totalContacts = contacts.size,
            favorites = contacts.count { it.contact.isFavorite },
            addedInRange = contacts.count { it.contact.createdAt >= rangeStart },
            monthly = monthly,
            byCategory = buildCategoryBreakdown(categoryCounts, categories),
            bySource = contacts.groupingBy { it.contact.source }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value },
            topTags = topTags,
            followUps = activity,
        )
    }

    /** [count] months ending at [end], oldest first. */
    private fun monthsBack(end: YearMonth, count: Int): List<YearMonth> = (count - 1 downTo 0).map { end.minusMonths(it.toLong()) }
}
