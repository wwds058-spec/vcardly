package com.yasin.vcardly.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.usecase.FollowUpManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the alarm firing and the notification's "Mark done" action. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderReceiverEntryPoint {
    fun repository(): FollowUpRepository
    fun manager(): FollowUpManager
    fun notifier(): ReminderNotifier
    fun clock(): Clock
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, ReminderReceiverEntryPoint::class.java)
        val repository = deps.repository()
        val manager = deps.manager()
        val notifier = deps.notifier()
        val clock = deps.clock()
        val id = intent.getLongExtra(ReminderIntents.EXTRA_FOLLOW_UP_ID, 0L)
        if (id == 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ReminderIntents.ACTION_FIRE -> fire(id, repository, notifier, clock)
                    ReminderIntents.ACTION_DONE -> {
                        manager.complete(id)
                        notifier.cancel(id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Re-checks the follow-up: it may have been completed, edited or deleted since the alarm was armed. */
    private suspend fun fire(id: Long, repository: FollowUpRepository, notifier: ReminderNotifier, clock: Clock) {
        val item = repository.getWithContact(id) ?: return
        val f = item.followUp
        if (f.status != FollowUpStatus.PENDING || !f.reminderEnabled || f.notifiedAt != null) return
        if (notifier.show(item)) repository.markNotified(id, clock.millis())
    }
}
