package com.yasin.vcardly.domain.reminder

import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpStatus
import java.util.concurrent.TimeUnit

/**
 * Decides what to do with reminders when the schedule must be rebuilt (reboot, time or zone change,
 * app update/launch). Pure so the tricky cases are unit-tested.
 *
 *  - not due yet            -> schedule an alarm
 *  - missed within 24 hours -> show it now (the device was off / time jumped)
 *  - missed longer ago      -> do not spam; mark handled (it still appears under Overdue)
 *  - already notified       -> nothing
 */
object ReminderPlanner {
    val CATCH_UP_WINDOW_MS: Long = TimeUnit.HOURS.toMillis(24)

    data class Scheduled(val followUp: FollowUp, val triggerAt: Long)

    data class Plan(
        val schedule: List<Scheduled>,
        val deliverNow: List<FollowUp>,
        val expire: List<FollowUp>,
    )

    fun triggerAt(followUp: FollowUp): Long = followUp.dueAt - TimeUnit.MINUTES.toMillis(followUp.reminderOffsetMinutes.toLong())

    fun isEligible(followUp: FollowUp): Boolean =
        followUp.status == FollowUpStatus.PENDING && followUp.reminderEnabled && followUp.notifiedAt == null

    fun plan(candidates: List<FollowUp>, now: Long): Plan {
        val schedule = mutableListOf<Scheduled>()
        val deliver = mutableListOf<FollowUp>()
        val expire = mutableListOf<FollowUp>()
        candidates.filter(::isEligible).forEach { f ->
            val t = triggerAt(f)
            when {
                t > now -> schedule += Scheduled(f, t)
                now - t <= CATCH_UP_WINDOW_MS -> deliver += f
                else -> expire += f
            }
        }
        return Plan(schedule, deliver.sortedBy { triggerAt(it) }, expire)
    }
}
