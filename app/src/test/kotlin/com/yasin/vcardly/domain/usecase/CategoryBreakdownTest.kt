package com.yasin.vcardly.domain.usecase

import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.CategoryCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBreakdownTest {
    private val a = Category(id = 1, name = "A", colorArgb = 0, sortOrder = 0)
    private val b = Category(id = 2, name = "B", colorArgb = 0, sortOrder = 1)

    @Test
    fun emptyOrZero_returnsEmpty() {
        assertTrue(buildCategoryBreakdown(emptyList(), listOf(a)).isEmpty())
        assertTrue(buildCategoryBreakdown(listOf(CategoryCount(1, 0)), listOf(a)).isEmpty())
    }

    @Test
    fun sortsBySizeThenSortOrder_uncategorisedLastOnTies_andComputesFractions() {
        val result = buildCategoryBreakdown(
            counts = listOf(CategoryCount(null, 2), CategoryCount(2, 2), CategoryCount(1, 4)),
            categories = listOf(a, b),
        )
        assertEquals(listOf(a, b, null), result.map { it.category })
        assertEquals(0.5f, result[0].fraction, 0.0001f)
        assertEquals(1f, result.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun unknownCategoryId_isTreatedAsUncategorised() {
        val result = buildCategoryBreakdown(listOf(CategoryCount(99, 3)), listOf(a))
        assertNull(result.single().category)
    }
}
