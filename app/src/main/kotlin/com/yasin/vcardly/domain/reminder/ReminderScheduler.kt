package com.yasin.vcardly.domain.reminder

import com.yasin.vcardly.domain.model.FollowUp

/** Arms/cancels the OS alarm for a follow-up's reminder. Implemented with AlarmManager. */
interface ReminderScheduler {
    /** Arms the alarm if the follow-up is eligible and its trigger time is in the future; otherwise cancels it. */
    fun schedule(followUp: FollowUp)
    fun cancel(followUpId: Long)

    /** Rebuilds every alarm and catches up on recently missed reminders. Safe to call any time. */
    suspend fun rescheduleAll()
}
