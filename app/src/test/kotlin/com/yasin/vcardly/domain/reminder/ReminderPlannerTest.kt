package com.yasin.vcardly.domain.reminder

import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpStatus
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val now = 1_000_000_000_000L
    private val hour = TimeUnit.HOURS.toMillis(1)

    private fun f(id: Long, due: Long, offset: Int = 0, status: FollowUpStatus = FollowUpStatus.PENDING,
                  enabled: Boolean = true, notified: Long? = null) =
        FollowUp(id = id, contactId = 1, title = "t", dueAt = due, reminderOffsetMinutes = offset,
            status = status, reminderEnabled = enabled, notifiedAt = notified)

    @Test fun futureReminder_isScheduled_atDueMinusOffset() {
        val plan = ReminderPlanner.plan(listOf(f(1, now + 5 * hour, offset = 60)), now)
        assertEquals(now + 4 * hour, plan.schedule.single().triggerAt)
        assertTrue(plan.deliverNow.isEmpty() && plan.expire.isEmpty())
    }

    @Test fun recentlyMissed_isDeliveredNow_oldestFirst() {
        val plan = ReminderPlanner.plan(listOf(f(1, now - hour), f(2, now - 3 * hour)), now)
        assertEquals(listOf(2L, 1L), plan.deliverNow.map { it.id })
    }

    @Test fun missedLongAgo_expiresWithoutNotifying() {
        val plan = ReminderPlanner.plan(listOf(f(1, now - 25 * hour)), now)
        assertEquals(listOf(1L), plan.expire.map { it.id })
        assertTrue(plan.deliverNow.isEmpty())
    }

    @Test fun boundary_exactly24HoursIsStillDelivered() {
        val plan = ReminderPlanner.plan(listOf(f(1, now - 24 * hour)), now)
        assertEquals(1, plan.deliverNow.size)
    }

    @Test fun ineligible_areIgnored() {
        val plan = ReminderPlanner.plan(listOf(
            f(1, now + hour, status = FollowUpStatus.COMPLETED),
            f(2, now + hour, enabled = false),
            f(3, now - hour, notified = now - 2 * hour),
        ), now)
        assertTrue(plan.schedule.isEmpty() && plan.deliverNow.isEmpty() && plan.expire.isEmpty())
    }

    @Test fun reminderOffsetMovesTriggerIntoThePast_thenCatchesUp() {
        // due in 10 minutes but reminder wanted 1 day before: trigger is already 23h50m ago
        val plan = ReminderPlanner.plan(listOf(f(1, now + TimeUnit.MINUTES.toMillis(10), offset = 1440)), now)
        assertEquals(1, plan.deliverNow.size)
    }
}
