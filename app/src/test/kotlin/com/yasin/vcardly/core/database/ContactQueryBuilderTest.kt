package com.yasin.vcardly.core.database

import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.ContactSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactQueryBuilderTest {

    @Test
    fun defaultFilter_hasNoWhereAndSortsByNameIgnoringCase() {
        val q = ContactQueryBuilder.build(ContactFilter())
        assertFalse(q.sql.contains("WHERE"))
        assertTrue(q.sql.endsWith("ORDER BY c.full_name COLLATE NOCASE ASC, c.id ASC"))
        assertTrue(q.args.isEmpty())
    }

    @Test
    fun searchText_isBoundNeverInlined() {
        val q = ContactQueryBuilder.build(ContactFilter(query = "x'; DROP TABLE contacts;--"))
        assertFalse(q.sql.contains("DROP"))
        // 8 columns + 1 tag subquery per token; the input has 4 whitespace-separated tokens.
        assertEquals(9 * 4, q.args.size)
        assertEquals(q.sql.count { it == '?' }, q.args.size)
    }

    @Test
    fun likeWildcards_areEscaped() {
        assertEquals("100\\%\\_\\\\", ContactQueryBuilder.escapeLike("100%_\\"))
        val q = ContactQueryBuilder.build(ContactFilter(query = "50%"))
        assertTrue(q.args.all { it == "%50\\%%" })
    }

    @Test
    fun blankQuery_addsNoClauses() {
        val q = ContactQueryBuilder.build(ContactFilter(query = "   "))
        assertFalse(q.sql.contains("WHERE"))
    }

    @Test
    fun categoryFavoritesAndTags_combineWithAnd() {
        val q = ContactQueryBuilder.build(
            ContactFilter(categoryId = 3, favoritesOnly = true, tagIds = setOf(9, 2)),
        )
        assertTrue(q.sql.contains("c.category_id = ?"))
        assertTrue(q.sql.contains("c.is_favorite = 1"))
        assertEquals(listOf<Any>(3L, 2L, 9L), q.args)
        assertEquals(q.sql.count { it == '?' }, q.args.size)
    }

    @Test
    fun everySortOption_producesOrderBy() {
        ContactSort.entries.forEach { sort ->
            val q = ContactQueryBuilder.build(ContactFilter(sort = sort))
            assertTrue("$sort", q.sql.contains(" ORDER BY "))
        }
    }
}
