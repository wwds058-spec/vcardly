package com.yasin.vcardly.data.repository

import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.core.database.mapper.toDomain
import com.yasin.vcardly.core.database.mapper.toEntity
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.domain.repository.FollowUpRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class FollowUpRepositoryImpl @Inject constructor(
    db: VCardlyDatabase,
    private val clock: Clock,
) : FollowUpRepository {
    private val dao = db.followUpDao()

    /** Day boundaries in the device's CURRENT zone, evaluated when the flow is created. */
    private fun dayBounds(): Pair<Long, Long> {
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val next = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return start to next
    }

    override fun observe(bucket: FollowUpBucket): Flow<List<FollowUpWithContact>> {
        val (start, next) = dayBounds()
        val source = when (bucket) {
            FollowUpBucket.OVERDUE -> dao.observeOverdue(start)
            FollowUpBucket.TODAY -> dao.observeToday(start, next)
            FollowUpBucket.UPCOMING -> dao.observeUpcoming(next)
            FollowUpBucket.COMPLETED -> dao.observeCompleted()
        }
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeForContact(contactId: Long): Flow<List<FollowUp>> =
        dao.observeForContact(contactId).map { rows -> rows.map { it.toDomain() } }

    override fun observeCounts(): Flow<FollowUpCounts> {
        val (start, next) = dayBounds()
        return combine(
            dao.observeTodayCount(start, next),
            dao.observeUpcomingCount(next),
            dao.observeOverdueCount(start),
            dao.observeCompletedCount(),
        ) { today, upcoming, overdue, completed ->
            FollowUpCounts(today = today, upcoming = upcoming, overdue = overdue, completed = completed)
        }
    }

    override suspend fun get(id: Long): FollowUp? = dao.getById(id)?.toDomain()

    override suspend fun save(followUp: FollowUp): Long {
        val now = clock.millis()
        return if (followUp.id == 0L) {
            dao.insert(followUp.toEntity().copy(createdAt = now, updatedAt = now))
        } else {
            dao.update(followUp.toEntity().copy(updatedAt = now))
            followUp.id
        }
    }

    override suspend fun markCompleted(id: Long) {
        val current = dao.getById(id) ?: return
        val now = clock.millis()
        dao.update(current.copy(status = FollowUpStatus.COMPLETED, completedAt = now, updatedAt = now))
    }

    override suspend fun reopen(id: Long) {
        val current = dao.getById(id) ?: return
        dao.update(
            current.copy(status = FollowUpStatus.PENDING, completedAt = null, updatedAt = clock.millis()),
        )
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun getReminderCandidates(): List<FollowUp> =
        dao.getReminderCandidates().map { it.toDomain() }
}
