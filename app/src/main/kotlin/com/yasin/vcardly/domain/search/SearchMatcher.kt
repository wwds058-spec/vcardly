package com.yasin.vcardly.domain.search

import com.yasin.vcardly.domain.model.FollowUpWithContact

/** Whitespace-separated terms; an item matches when EVERY term appears in at least one searchable text (case-insensitive). */
object SearchMatcher {
    fun terms(query: String): List<String> = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(terms: List<String>, vararg texts: String): Boolean {
        if (terms.isEmpty()) return false
        val haystacks = texts.map { it.lowercase() }
        return terms.all { t -> haystacks.any { it.contains(t) } }
    }

    fun followUps(query: String, all: List<FollowUpWithContact>): List<FollowUpWithContact> {
        val t = terms(query)
        return all.filter { matches(t, it.followUp.title, it.followUp.notes, it.contactName, it.contactCompany) }
    }

    /** Ids of categories whose (localized) display name matches. [names] maps category id to the name shown to the user. */
    fun categoryIds(query: String, names: Map<Long, String>): List<Long> {
        val t = terms(query)
        return names.filter { (_, n) -> matches(t, n) }.keys.toList()
    }
}
