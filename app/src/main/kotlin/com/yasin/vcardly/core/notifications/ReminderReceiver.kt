package com.yasin.vcardly.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.usecase.FollowUpManager
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the alarm firing and the notification's "Mark done" action. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: FollowUpRepository
    @Inject lateinit var manager: FollowUpManager
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Hilt injection happens here
        val id = intent.getLongExtra(ReminderIntents.EXTRA_FOLLOW_UP_ID, 0L)
        if (id == 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ReminderIntents.ACTION_FIRE -> fire(id)
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
    private suspend fun fire(id: Long) {
        val item = repository.getWithContact(id) ?: return
        val f = item.followUp
        if (f.status != FollowUpStatus.PENDING || !f.reminderEnabled || f.notifiedAt != null) return
        if (notifier.show(item)) repository.markNotified(id, clock.millis())
    }
}
