package com.yasin.vcardly.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How a contact entered the app. */
enum class ContactSource { MANUAL, SCAN, IMPORT }

enum class ContactSort { NAME_ASC, NAME_DESC, COMPANY_ASC, RECENTLY_ADDED, RECENTLY_UPDATED }

enum class FollowUpType { CALL, EMAIL, MEETING, MESSAGE, OTHER }

/**
 * Persisted status. Today / Upcoming / Overdue are derived from [PENDING] + due time,
 * so they can never drift out of sync with the clock.
 */
enum class FollowUpStatus { PENDING, COMPLETED }

/** Seeded categories. Display names come from string resources, so they localize. */
enum class SystemCategory(val key: String, val colorArgb: Long) {
    CLIENT("client", 0xFF3F51B5),
    PARTNER("partner", 0xFF00897B),
    VENDOR("vendor", 0xFFF57C00),
    COLLEAGUE("colleague", 0xFF7B1FA2),
    FRIEND("friend", 0xFFD81B60),
    OTHER("other", 0xFF607D8B);

    companion object {
        fun fromKey(key: String?): SystemCategory? = entries.firstOrNull { it.key == key }
    }
}
