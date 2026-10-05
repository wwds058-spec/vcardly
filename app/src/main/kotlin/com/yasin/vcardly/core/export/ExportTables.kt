package com.yasin.vcardly.core.export

import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.model.FollowUpWithContact
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A sheet / CSV file: header row plus string cells. */
data class Table(val name: String, val header: List<String>, val rows: List<List<String>>)

/** All user-visible words in an export, resolved from string resources by the caller (so exports localize). */
data class ExportLabels(
    val contactsSheet: String,
    val followUpsSheet: String,
    val contactHeaders: List<String>,
    val followUpHeaders: List<String>,
    val yes: String,
    val no: String,
    val source: Map<ContactSource, String>,
    val type: Map<FollowUpType, String>,
    val status: Map<FollowUpStatus, String>,
) {
    init {
        require(contactHeaders.size == ExportTables.CONTACT_COLUMNS) { "contact header count" }
        require(followUpHeaders.size == ExportTables.FOLLOW_UP_COLUMNS) { "follow-up header count" }
    }
}

object ExportTables {
    const val CONTACT_COLUMNS = 15
    const val FOLLOW_UP_COLUMNS = 8
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun contacts(list: List<ContactDetails>, categoryName: (Category) -> String, labels: ExportLabels, zone: ZoneId): Table =
        Table(
            labels.contactsSheet,
            labels.contactHeaders,
            list.map { d ->
                val c = d.contact
                listOf(
                    c.fullName, c.jobTitle, c.company, c.phone, c.phoneAlt, c.email, c.emailAlt, c.website, c.address,
                    d.category?.let(categoryName).orEmpty(),
                    d.tags.joinToString("; ") { it.name },
                    if (c.isFavorite) labels.yes else labels.no,
                    labels.source[c.source].orEmpty(),
                    c.notes,
                    format(c.createdAt, zone),
                )
            },
        )

    fun followUps(list: List<FollowUpWithContact>, labels: ExportLabels, zone: ZoneId): Table =
        Table(
            labels.followUpsSheet,
            labels.followUpHeaders,
            list.map { f ->
                val x = f.followUp
                listOf(
                    f.contactName, f.contactCompany, x.title, labels.type[x.type].orEmpty(), labels.status[x.status].orEmpty(),
                    format(x.dueAt, zone), x.completedAt?.let { format(it, zone) }.orEmpty(), x.notes,
                )
            },
        )

    private fun format(millis: Long, zone: ZoneId): String = stamp.format(Instant.ofEpochMilli(millis).atZone(zone))
}
