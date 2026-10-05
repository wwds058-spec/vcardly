package com.yasin.vcardly.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yasin.vcardly.domain.reminder.ReminderScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerEntryPoint {
    fun scheduler(): ReminderScheduler
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val scheduler = EntryPointAccessors.fromApplication(applicationContext, ReminderWorkerEntryPoint::class.java).scheduler()
        return try {
            scheduler.rescheduleAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val ONE_TIME = "reminders-reschedule"
        private const val PERIODIC = "reminders-safety-net"

        /** Coalesces bursts (e.g. TIME_SET + TIMEZONE_CHANGED) into one run. */
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RescheduleWorker>().build(),
            )
        }

        /** Belt and braces: re-arms alarms twice a day in case a system broadcast was missed. */
        fun ensurePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build(),
            )
        }
    }
}
