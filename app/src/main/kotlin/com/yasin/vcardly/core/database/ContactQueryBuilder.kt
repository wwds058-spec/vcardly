package com.yasin.vcardly.core.database

import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.ContactSort

/** SQL text plus positional bind arguments. Pure Kotlin so it is unit-testable on the JVM. */
data class BuiltQuery(val sql: String, val args: List<Any>)

/**
 * Builds the contact list query. User input only ever reaches SQLite as bound arguments;
 * the ORDER BY clause comes from a closed enum, never from input.
 *
 * Search: the text is split on whitespace and every token must match at least one searchable
 * column or tag name (case-insensitive, LIKE wildcards in the input are escaped).
 */
object ContactQueryBuilder {
    private val searchColumns = listOf(
        "c.full_name", "c.company", "c.job_title", "c.email", "c.email_alt",
        "c.phone", "c.phone_alt", "c.notes",
    )

    fun build(filter: ContactFilter): BuiltQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        filter.query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { token ->
            val pattern = "%${escapeLike(token)}%"
            val tagClause = "EXISTS (SELECT 1 FROM contact_tags st JOIN tags stag ON stag.id = st.tag_id " +
                "WHERE st.contact_id = c.id AND stag.name LIKE ? ESCAPE '\\')"
            val clauses = searchColumns.map { "$it LIKE ? ESCAPE '\\'" } + tagClause
            where += clauses.joinToString(separator = " OR ", prefix = "(", postfix = ")")
            repeat(clauses.size) { args += pattern }
        }

        filter.categoryId?.let {
            where += "c.category_id = ?"
            args += it
        }
        if (filter.favoritesOnly) where += "c.is_favorite = 1"
        filter.tagIds.sorted().forEach { tagId ->
            where += "EXISTS (SELECT 1 FROM contact_tags ft WHERE ft.contact_id = c.id AND ft.tag_id = ?)"
            args += tagId
        }

        val sql = buildString {
            append("SELECT c.* FROM contacts c")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY ").append(orderBy(filter.sort))
        }
        return BuiltQuery(sql, args)
    }

    internal fun escapeLike(input: String): String =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun orderBy(sort: ContactSort): String = when (sort) {
        ContactSort.NAME_ASC -> "c.full_name COLLATE NOCASE ASC, c.id ASC"
        ContactSort.NAME_DESC -> "c.full_name COLLATE NOCASE DESC, c.id DESC"
        ContactSort.COMPANY_ASC -> "(c.company = '') ASC, c.company COLLATE NOCASE ASC, c.full_name COLLATE NOCASE ASC"
        ContactSort.RECENTLY_ADDED -> "c.created_at DESC, c.id DESC"
        ContactSort.RECENTLY_UPDATED -> "c.updated_at DESC, c.id DESC"
    }
}
