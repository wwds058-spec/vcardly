package com.yasin.vcardly.domain.report

import com.yasin.vcardly.core.export.BarRow
import com.yasin.vcardly.core.export.PdfBlock
import com.yasin.vcardly.core.export.PdfDoc
import java.text.NumberFormat
import java.time.YearMonth

/** Every word in the PDF, already localized by the caller. */
data class ReportStrings(
    val title: String,
    val generatedOn: String,
    val rangeLabel: String,
    val summary: String,
    val totalContacts: String,
    val favorites: String,
    val addedInRange: String,
    val addedPerMonth: String,
    val followUps: String,
    val followUpsDue: String,
    val followUpsCompleted: String,
    val followUpsOverdue: String,
    val completionRate: String,
    val followUpsByType: String,
    val byCategory: String,
    val uncategorised: String,
    val bySource: String,
    val topTags: String,
    val directory: String,
    val directoryHeader: List<String>,
    val none: String,
)

/**
 * Builds the PDF content from a [Report] and a contact directory. Pure: the same inputs always give the same document,
 * so it is unit-tested. Category/source/type names arrive pre-localized in the lambdas.
 */
object ReportPdfContent {
    fun build(
        report: Report,
        strings: ReportStrings,
        categoryName: (com.yasin.vcardly.domain.model.Category?) -> String,
        sourceName: (com.yasin.vcardly.domain.model.ContactSource) -> String,
        typeName: (com.yasin.vcardly.domain.model.FollowUpType) -> String,
        monthLabel: (YearMonth) -> String,
        directoryRows: List<List<String>>,
        number: NumberFormat,
    ): PdfDoc {
        fun bars(items: List<Pair<String, Int>>): PdfBlock {
            val max = items.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            return PdfBlock.Bars(items.map { BarRow(it.first, number.format(it.second), it.second.toFloat() / max) })
        }
        val f = report.followUps
        val blocks = mutableListOf<PdfBlock>()

        blocks += PdfBlock.Heading(strings.summary)
        blocks += PdfBlock.KeyValues(
            listOf(
                strings.totalContacts to number.format(report.totalContacts),
                strings.favorites to number.format(report.favorites),
                strings.addedInRange to number.format(report.addedInRange),
            ),
        )

        blocks += PdfBlock.Heading(strings.addedPerMonth)
        blocks += bars(report.monthly.map { monthLabel(it.month) to it.count })

        blocks += PdfBlock.Heading(strings.followUps)
        blocks += PdfBlock.KeyValues(
            listOf(
                strings.followUpsDue to number.format(f.dueSoFar),
                strings.followUpsCompleted to number.format(f.completed),
                strings.followUpsOverdue to number.format(f.overdue),
                strings.completionRate to (f.completionRate?.let { pct(it, number) } ?: strings.none),
            ),
        )
        if (f.byType.isNotEmpty()) {
            blocks += PdfBlock.Paragraph(strings.followUpsByType)
            blocks += bars(f.byType.map { typeName(it.first) to it.second })
        }

        blocks += PdfBlock.Heading(strings.byCategory)
        blocks += if (report.byCategory.isEmpty()) PdfBlock.Paragraph(strings.none)
        else bars(report.byCategory.map { (categoryName(it.category).ifBlank { strings.uncategorised }) to it.count })

        blocks += PdfBlock.Heading(strings.bySource)
        blocks += if (report.bySource.isEmpty()) PdfBlock.Paragraph(strings.none) else bars(report.bySource.map { sourceName(it.first) to it.second })

        blocks += PdfBlock.Heading(strings.topTags)
        blocks += if (report.topTags.isEmpty()) PdfBlock.Paragraph(strings.none) else bars(report.topTags.map { "#${it.first}" to it.second })

        if (directoryRows.isNotEmpty()) {
            blocks += PdfBlock.Heading(strings.directory)
            blocks += PdfBlock.TableBlock(strings.directoryHeader, directoryRows, listOf(3f, 3f, 3f, 4f))
        }
        return PdfDoc(strings.title, "${strings.generatedOn} · ${strings.rangeLabel}", blocks)
    }

    private fun pct(fraction: Float, number: NumberFormat): String = number.format((fraction * 100).toInt()) + "%"
}
