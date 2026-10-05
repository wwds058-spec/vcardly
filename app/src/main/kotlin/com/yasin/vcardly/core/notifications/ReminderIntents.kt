package com.yasin.vcardly.core.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Explicit, per-follow-up intents (unique by data URI) for our own receivers. */
object ReminderIntents {
    const val ACTION_FIRE = "com.yasin.vcardly.action.REMINDER_FIRE"
    const val ACTION_DONE = "com.yasin.vcardly.action.REMINDER_DONE"
    const val EXTRA_FOLLOW_UP_ID = "follow_up_id"

    fun fire(context: Context, id: Long): Intent = build(context, ACTION_FIRE, id)
    fun markDone(context: Context, id: Long): Intent = build(context, ACTION_DONE, id)

    private fun build(context: Context, action: String, id: Long) =
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("vcardly://followup/$id/${action.substringAfterLast('.')}"))
            .putExtra(EXTRA_FOLLOW_UP_ID, id)
}
