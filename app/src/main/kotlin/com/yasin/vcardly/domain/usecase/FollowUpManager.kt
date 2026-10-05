package com.yasin.vcardly.domain.usecase

import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.reminder.ReminderScheduler
import com.yasin.vcardly.domain.repository.FollowUpRepository
import javax.inject.Inject
import javax.inject.Singleton

/** All follow-up writes go through here so the stored state and the OS alarm can never disagree. */
@Singleton
class FollowUpManager @Inject constructor(
    private val repository: FollowUpRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun save(followUp: FollowUp): Long {
        val id = repository.save(followUp)
        repository.get(id)?.let(scheduler::schedule)
        return id
    }

    suspend fun complete(id: Long) {
        repository.markCompleted(id)
        scheduler.cancel(id)
    }

    suspend fun reopen(id: Long) {
        repository.reopen(id)
        repository.get(id)?.let(scheduler::schedule)
    }

    suspend fun delete(id: Long) {
        repository.delete(id)
        scheduler.cancel(id)
    }
}
