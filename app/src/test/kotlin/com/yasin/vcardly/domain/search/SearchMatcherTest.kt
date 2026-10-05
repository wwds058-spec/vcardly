package com.yasin.vcardly.domain.search

import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpWithContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    private fun fu(id: Long, title: String, notes: String = "", name: String = "Asha", company: String = "") =
        FollowUpWithContact(FollowUp(id = id, contactId = 1, title = title, notes = notes, dueAt = 1), name, company)

    @Test fun termsAreAnded_acrossFields_caseInsensitively() {
        val all = listOf(fu(1, "Send proposal", name = "Asha Rao", company = "Acme"), fu(2, "Call", notes = "about the PROPOSAL"), fu(3, "Lunch"))
        assertEquals(listOf(1L, 2L), SearchMatcher.followUps("proposal", all).map { it.followUp.id })
        assertEquals(listOf(1L), SearchMatcher.followUps("  PROPOSAL acme ", all).map { it.followUp.id })
        assertEquals(listOf(1L), SearchMatcher.followUps("asha rao", all).map { it.followUp.id })
    }

    @Test fun blankQueryMatchesNothing() {
        assertTrue(SearchMatcher.followUps("   ", listOf(fu(1, "x"))).isEmpty())
        assertFalse(SearchMatcher.matches(emptyList(), "anything"))
    }

    @Test fun categoryIdsUseTheLocalizedDisplayNames() {
        val names = mapOf(1L to "Client", 2L to "ग्राहक", 3L to "Investors")
        assertEquals(listOf(2L), SearchMatcher.categoryIds("ग्राहक", names))
        assertEquals(listOf(1L), SearchMatcher.categoryIds("cli", names))
        assertTrue(SearchMatcher.categoryIds("", names).isEmpty())
    }
}
