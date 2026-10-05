package com.yasin.vcardly

import android.app.Application
import com.yasin.vcardly.core.notifications.ReminderNotifier
import com.yasin.vcardly.core.notifications.RescheduleWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VCardlyApplication : Application() {
    @Inject lateinit var reminderNotifier: ReminderNotifier

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannel()
        // Opening the app re-arms alarms (force-stop clears them with no broadcast), and keeps a periodic safety net.
        RescheduleWorker.enqueueNow(this)
        RescheduleWorker.ensurePeriodic(this)
    }
}
