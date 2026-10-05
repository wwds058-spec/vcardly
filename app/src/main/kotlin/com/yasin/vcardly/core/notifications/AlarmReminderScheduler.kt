package com.yasin.vcardly.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.yasin.vcardly.core.common.AppLog
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.reminder.ReminderPlanner
import com.yasin.vcardly.domain.reminder.ReminderScheduler
import com.yasin.vcardly.domain.repository.FollowUpRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager-backed reminders. Alarms are wall-clock (RTC) so they follow the user's clock, and they are
 * lost on reboot / force-stop / time change, which is why [rescheduleAll] runs after each of those.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FollowUpRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : ReminderScheduler {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(followUp: FollowUp) {
        val trigger = ReminderPlanner.triggerAt(followUp)
        if (!ReminderPlanner.isEligible(followUp) || trigger <= clock.millis()) {
            cancel(followUp.id)
        } else {
            setAlarm(followUp.id, trigger)
        }
    }

    override fun cancel(followUpId: Long) {
        alarmManager.cancel(pendingIntent(followUpId))
        notifier.cancel(followUpId)
    }

    override suspend fun rescheduleAll() {
        val now = clock.millis()
        val plan = ReminderPlanner.plan(repository.getReminderCandidates(), now)
        plan.schedule.forEach { setAlarm(it.followUp.id, it.triggerAt) }
        plan.deliverNow.forEach { f ->
            val item = repository.getWithContact(f.id) ?: return@forEach
            // Left un-notified when notifications are blocked, so it can still be delivered once allowed.
            if (notifier.show(item)) repository.markNotified(f.id, now)
        }
        plan.expire.forEach { repository.markNotified(it.id, now) }
        AppLog.d(TAG, "rescheduled scheduled=${plan.schedule.size} delivered=${plan.deliverNow.size} expired=${plan.expire.size}")
    }

    private fun setAlarm(id: Long, triggerAt: Long) {
        val pi = pendingIntent(id)
        try {
            if (ReminderPermissions.canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            // Exact-alarm access was revoked between the check and the call; inexact still delivers.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0, ReminderIntents.fire(context, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "Reminders"
    }
}
