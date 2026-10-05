package com.yasin.vcardly.core.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yasin.vcardly.MainActivity
import com.yasin.vcardly.R
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.model.FollowUpWithContact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts follow-up reminders. The lock screen only ever shows a generic message: names and titles are
 * personal data, so the detailed text is marked private (hidden while the device is locked).
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notif_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Returns true only if a notification was actually posted (false when notifications are blocked). */
    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled(), which is false without POST_NOTIFICATIONS
    fun show(item: FollowUpWithContact): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel()
        val followUp = item.followUp

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("vcardly://followup/${followUp.id}"))
                .putExtra(EXTRA_OPEN_CONTACT_ID, followUp.contactId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = PendingIntent.getBroadcast(
            context,
            0,
            ReminderIntents.markDone(context, followUp.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_public_title))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(followUp.title)
            .setContentText(context.getString(typeLabel(followUp.type), item.contactName))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notif_action_done), done)
            .build()
        manager.notify(notificationId(followUp.id), notification)
        return true
    }

    fun cancel(followUpId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(followUpId))
    }

    private fun typeLabel(type: FollowUpType): Int = when (type) {
        FollowUpType.CALL -> R.string.notif_text_call
        FollowUpType.EMAIL -> R.string.notif_text_email
        FollowUpType.MEETING -> R.string.notif_text_meeting
        FollowUpType.MESSAGE -> R.string.notif_text_message
        FollowUpType.OTHER -> R.string.notif_text_other
    }

    companion object {
        const val CHANNEL_ID = "follow_up_reminders"
        const val EXTRA_OPEN_CONTACT_ID = "open_contact_id"
        fun notificationId(followUpId: Long): Int = followUpId.hashCode()
    }
}
