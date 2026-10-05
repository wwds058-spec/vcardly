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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class FollowUpRepositoryImpl @Inject constructor(
    db: VCardlyDatabase,
    private val clock: Clock,
) : FollowUpRepository {
    private val dao = db.followUpDao()

    /** [start of today, start of tomorrow) in the device's current zone. */
    private fun dayBounds(): Pair<Long, Long> {
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val next = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return start to next
    }

    /** Emits the current day bounds and re-emits just after each midnight so Today/Overdue roll over while the screen is open. */
    private fun dayBoundsFlow(): Flow<Pair<Long, Long>> = flow {
        while (true) {
            val bounds = dayBounds()
            emit(bounds)
            delay((bounds.second - clock.millis()).coerceAtLeast(1_000L) + 1_000L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(bucket: FollowUpBucket): Flow<List<FollowUpWithContact>> =
        dayBoundsFlow().flatMapLatest { (start, next) ->
            when (bucket) {
                FollowUpBucket.OVERDUE -> dao.observeOverdue(start)
                FollowUpBucket.TODAY -> dao.observeToday(start, next)
                FollowUpBucket.UPCOMING -> dao.observeUpcoming(next)
                FollowUpBucket.COMPLETED -> dao.observeCompleted()
            }
        }.map { rows -> rows.map { it.toDomain() } }

    override fun observeForContact(contactId: Long): Flow<List<FollowUp>> =
        dao.observeForContact(contactId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<List<FollowUpWithContact>> =
        dao.observeAllWithContact().map { rows -> rows.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCounts(): Flow<FollowUpCounts> = dayBoundsFlow().flatMapLatest { (start, next) ->
        combine(
            dao.observeTodayCount(start, next),
            dao.observeUpcomingCount(next),
            dao.observeOverdueCount(start),
            dao.observeCompletedCount(),
        ) { today, upcoming, overdue, completed ->
            FollowUpCounts(today = today, upcoming = upcoming, overdue = overdue, completed = completed)
        }
    }

    override suspend fun get(id: Long): FollowUp? = dao.getById(id)?.toDomain()

    override suspend fun getWithContact(id: Long): FollowUpWithContact? = dao.getWithContact(id)?.toDomain()

    override suspend fun markNotified(id: Long, at: Long) = dao.markNotified(id, at)

    override suspend fun save(followUp: FollowUp): Long {
        val now = clock.millis()
        if (followUp.id == 0L) {
            return dao.insert(followUp.toEntity().copy(notifiedAt = null, completedAt = null, createdAt = now, updatedAt = now))
        }
        val existing = dao.getById(followUp.id)
        // A changed schedule must be able to notify again; unchanged keeps its "already notified" state.
        val scheduleChanged = existing == null || existing.dueAt != followUp.dueAt ||
            existing.reminderOffsetMinutes != followUp.reminderOffsetMinutes ||
            existing.reminderEnabled != followUp.reminderEnabled
        dao.update(
            followUp.toEntity().copy(
                notifiedAt = if (scheduleChanged) null else existing?.notifiedAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return followUp.id
    }

    override suspend fun markCompleted(id: Long) {
        val current = dao.getById(id) ?: return
        val now = clock.millis()
        dao.update(current.copy(status = FollowUpStatus.COMPLETED, completedAt = now, updatedAt = now))
    }

    override suspend fun reopen(id: Long) {
        val current = dao.getById(id) ?: return
        dao.update(
            current.copy(status = FollowUpStatus.PENDING, completedAt = null, notifiedAt = null, updatedAt = clock.millis()),
        )
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun getReminderCandidates(): List<FollowUp> =
        dao.getReminderCandidates().map { it.toDomain() }
}
