package com.yasin.vcardly.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How a contact entered the app. */
enum class ContactSource { MANUAL, SCAN, IMPORT }

enum class ContactSort { NAME_ASC, NAME_DESC, COMPANY_ASC, RECENTLY_ADDED, RECENTLY_UPDATED }

/**
 * Stored by name. [MESSAGE] is kept so follow-ups saved by earlier versions still load; new ones use [WHATSAPP] or
 * [EMAIL] instead, so it is not offered in the picker (see [FollowUpType.pickable]).
 */
enum class FollowUpType {
    CALL, WHATSAPP, EMAIL, MEETING, QUOTATION, PAYMENT, MESSAGE, OTHER;

    companion object {
        val pickable: List<FollowUpType> = listOf(CALL, WHATSAPP, EMAIL, MEETING, QUOTATION, PAYMENT, OTHER)
    }
}

/**
 * Persisted status. Today / Upcoming / Overdue are derived from the active statuses + due time, so they can never drift
 * out of sync with the clock. [RESCHEDULED] is still active (it was moved to a new time); [CANCELLED] is closed without
 * being done. Older app versions read unknown names as PENDING (see Converters).
 */
enum class FollowUpStatus {
    PENDING, RESCHEDULED, COMPLETED, CANCELLED;

    /** Still to do: shown in Today / Upcoming / Overdue and gets reminders. */
    val isActive: Boolean get() = this == PENDING || this == RESCHEDULED
}

/**
 * Seeded categories. Display names come from string resources, so they localize. The order is the order on a fresh
 * install; categories added in later versions are appended for existing users (see SystemCategorySeeder). Colours are
 * checked together for colour-blind separation (dataviz validator) and against black/white ink (ContrastTest).
 */
enum class SystemCategory(val key: String, val colorArgb: Long) {
    BUSINESS("business", 0xFF0277BD),
    CUSTOMER("customer", 0xFF2E7D32),
    CLIENT("client", 0xFF3F51B5),
    SUPPLIER("supplier", 0xFFA0522D),
    VENDOR("vendor", 0xFFF57C00),
    PARTNER("partner", 0xFF00897B),
    COLLEAGUE("colleague", 0xFF7B1FA2),
    FRIEND("friend", 0xFFD81B60),
    OTHER("other", 0xFF607D8B);

    companion object {
        fun fromKey(key: String?): SystemCategory? = entries.firstOrNull { it.key == key }
    }
}

/** Colours assigned to user-created categories, cycled by position (no colour picker yet). */
object CategoryPalette {
    val colors: List<Long> = listOf(0xFF5E35B1, 0xFF00ACC1, 0xFF7CB342, 0xFFE53935, 0xFF6D4C41, 0xFF1E88E5, 0xFFFB8C00, 0xFF8E24AA)
    fun colorFor(index: Int): Long = colors[index.mod(colors.size)]
}
