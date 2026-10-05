package com.yasin.vcardly.domain.usecase

import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.CategoryCount

/** [category] is null for contacts that have no category. [fraction] is of all counted contacts (0..1). */
data class CategoryBreakdownItem(val category: Category?, val count: Int, val fraction: Float)

/**
 * Joins per-category counts with category rows, drops zero counts and orders by size
 * (ties: category sort order, uncategorised last) so the dashboard is stable between emissions.
 */
fun buildCategoryBreakdown(counts: List<CategoryCount>, categories: List<Category>): List<CategoryBreakdownItem> {
    val byId = categories.associateBy { it.id }
    val total = counts.sumOf { it.count }
    if (total == 0) return emptyList()
    return counts
        .filter { it.count > 0 }
        .map { CategoryBreakdownItem(it.categoryId?.let(byId::get), it.count, it.count.toFloat() / total) }
        .sortedWith(
            compareByDescending<CategoryBreakdownItem> { it.count }
                .thenBy { it.category == null }
                .thenBy { it.category?.sortOrder ?: Int.MAX_VALUE },
        )
}
