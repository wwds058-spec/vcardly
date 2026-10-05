package com.yasin.vcardly.di

import com.yasin.vcardly.core.notifications.AlarmReminderScheduler
import com.yasin.vcardly.domain.reminder.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds abstract fun scheduler(impl: AlarmReminderScheduler): ReminderScheduler
}
