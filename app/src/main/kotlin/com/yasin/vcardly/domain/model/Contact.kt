package com.yasin.vcardly.domain.model

/** All timestamps are epoch milliseconds (UTC). Image paths are relative to app-private storage. */
data class Contact(
    val id: Long = 0,
    val fullName: String,
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val phoneAlt: String = "",
    val email: String = "",
    val emailAlt: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val categoryId: Long? = null,
    val isFavorite: Boolean = false,
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val source: ContactSource = ContactSource.MANUAL,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class Category(
    val id: Long = 0,
    /** Blank for [systemCategory] rows; use the localized resource name instead. */
    val name: String,
    val colorArgb: Long,
    val systemCategory: SystemCategory? = null,
    val sortOrder: Int = 0,
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val colorArgb: Long? = null,
)

data class TagWithCount(val tag: Tag, val contactCount: Int)

data class ContactDetails(
    val contact: Contact,
    val category: Category?,
    val tags: List<Tag>,
)

data class ContactFilter(
    val query: String = "",
    val categoryId: Long? = null,
    /** A contact must carry ALL of these tags. */
    val tagIds: Set<Long> = emptySet(),
    val favoritesOnly: Boolean = false,
    val sort: ContactSort = ContactSort.NAME_ASC,
)

data class CategoryCount(val categoryId: Long?, val count: Int)

data class ContactStats(
    val total: Int,
    val favorites: Int,
    val addedSince: Int,
    val byCategory: List<CategoryCount>,
)
