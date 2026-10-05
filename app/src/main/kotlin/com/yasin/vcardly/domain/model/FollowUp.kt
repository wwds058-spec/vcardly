package com.yasin.vcardly.domain.model

data class FollowUp(
    val id: Long = 0,
    val contactId: Long,
    val type: FollowUpType = FollowUpType.CALL,
    val status: FollowUpStatus = FollowUpStatus.PENDING,
    val title: String,
    val notes: String = "",
    /** Epoch millis (UTC) at which the follow-up is due. */
    val dueAt: Long,
    val reminderEnabled: Boolean = true,
    /** Minutes before [dueAt] the reminder fires (0 = at due time). */
    val reminderOffsetMinutes: Int = 0,
    val completedAt: Long? = null,
    /** When the reminder notification was actually shown; null = not yet. Reset when the schedule changes. */
    val notifiedAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class FollowUpWithContact(
    val followUp: FollowUp,
    val contactName: String,
    val contactCompany: String,
)

enum class FollowUpBucket { TODAY, UPCOMING, OVERDUE, COMPLETED }

data class FollowUpCounts(
    val today: Int,
    val upcoming: Int,
    val overdue: Int,
    val completed: Int,
)

/** Reminder lead times offered in the UI, in minutes before the due time. */
object ReminderOffsets {
    val options: List<Int> = listOf(0, 15, 60, 1440)
}
