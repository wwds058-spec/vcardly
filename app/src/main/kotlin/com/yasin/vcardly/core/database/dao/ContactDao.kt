package com.yasin.vcardly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.yasin.vcardly.core.database.entity.CategoryCountRow
import com.yasin.vcardly.core.database.entity.CategoryEntity
import com.yasin.vcardly.core.database.entity.ContactEntity
import com.yasin.vcardly.core.database.entity.ContactTagCrossRef
import com.yasin.vcardly.core.database.entity.ContactWithRelations
import com.yasin.vcardly.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity): Int

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): ContactEntity?

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getDetails(id: Long): ContactWithRelations?

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeDetails(id: Long): Flow<ContactWithRelations?>

    /** Filtered/sorted list. SQL is produced by ContactQueryBuilder with bound arguments only. */
    @Transaction
    @RawQuery(
        observedEntities = [
            ContactEntity::class,
            CategoryEntity::class,
            TagEntity::class,
            ContactTagCrossRef::class,
        ],
    )
    fun observeFiltered(query: SupportSQLiteQuery): Flow<List<ContactWithRelations>>

    @Query("UPDATE contacts SET is_favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagLinks(links: List<ContactTagCrossRef>)

    @Query("DELETE FROM contact_tags WHERE contact_id = :contactId")
    suspend fun clearTagLinks(contactId: Long)

    @Query("SELECT COUNT(*) FROM contacts WHERE source = 'SCAN' AND created_at >= :since")
    suspend fun countScannedSince(since: Long): Int

    // ---- backup / restore ----

    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contact_tags")
    suspend fun getAllTagLinks(): List<ContactTagCrossRef>

    @Query("DELETE FROM contact_tags")
    suspend fun deleteAllTagLinks()

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    // ---- stats for the dashboard / reports ----

    @Query("SELECT COUNT(*) FROM contacts")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts WHERE is_favorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM contacts WHERE created_at >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT category_id, COUNT(*) AS count FROM contacts GROUP BY category_id")
    fun observeCategoryCounts(): Flow<List<CategoryCountRow>>
}
