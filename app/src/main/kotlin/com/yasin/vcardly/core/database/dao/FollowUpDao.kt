package com.yasin.vcardly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yasin.vcardly.core.database.entity.FollowUpEntity
import com.yasin.vcardly.core.database.entity.FollowUpWithContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * Bucket boundaries are passed in by the repository (computed from the injected Clock and
 * the device time zone) so the DAO stays free of time logic.
 */
@Dao
interface FollowUpDao {
    @Insert
    suspend fun insert(followUp: FollowUpEntity): Long

    @Update
    suspend fun update(followUp: FollowUpEntity): Int

    @Query("DELETE FROM follow_ups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM follow_ups WHERE id = :id")
    suspend fun getById(id: Long): FollowUpEntity?

    @Transaction
    @Query("SELECT * FROM follow_ups WHERE id = :id")
    suspend fun getWithContact(id: Long): FollowUpWithContactEntity?

    @Transaction
    @Query("SELECT * FROM follow_ups ORDER BY due_at DESC")
    fun observeAllWithContact(): Flow<List<FollowUpWithContactEntity>>

    @Query("SELECT * FROM follow_ups")
    suspend fun getAll(): List<FollowUpEntity>

    @Query("DELETE FROM follow_ups")
    suspend fun deleteAll()

    @Query("UPDATE follow_ups SET notified_at = :at WHERE id = :id")
    suspend fun markNotified(id: Long, at: Long)

    @Transaction
    @Query("SELECT * FROM follow_ups WHERE status = 'PENDING' AND due_at < :startOfToday ORDER BY due_at ASC")
    fun observeOverdue(startOfToday: Long): Flow<List<FollowUpWithContactEntity>>

    @Transaction
    @Query(
        "SELECT * FROM follow_ups WHERE status = 'PENDING' AND due_at >= :startOfToday AND due_at < :startOfTomorrow ORDER BY due_at ASC",
    )
    fun observeToday(startOfToday: Long, startOfTomorrow: Long): Flow<List<FollowUpWithContactEntity>>

    @Transaction
    @Query("SELECT * FROM follow_ups WHERE status = 'PENDING' AND due_at >= :startOfTomorrow ORDER BY due_at ASC")
    fun observeUpcoming(startOfTomorrow: Long): Flow<List<FollowUpWithContactEntity>>

    @Transaction
    @Query("SELECT * FROM follow_ups WHERE status = 'COMPLETED' ORDER BY completed_at DESC")
    fun observeCompleted(): Flow<List<FollowUpWithContactEntity>>

    @Query("SELECT * FROM follow_ups WHERE contact_id = :contactId ORDER BY due_at DESC")
    fun observeForContact(contactId: Long): Flow<List<FollowUpEntity>>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE status = 'PENDING' AND due_at < :startOfToday")
    fun observeOverdueCount(startOfToday: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM follow_ups WHERE status = 'PENDING' AND due_at >= :startOfToday AND due_at < :startOfTomorrow",
    )
    fun observeTodayCount(startOfToday: Long, startOfTomorrow: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE status = 'PENDING' AND due_at >= :startOfTomorrow")
    fun observeUpcomingCount(startOfTomorrow: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT * FROM follow_ups WHERE status = 'PENDING' AND reminder_enabled = 1")
    suspend fun getReminderCandidates(): List<FollowUpEntity>
}
