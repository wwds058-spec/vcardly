package com.yasin.vcardly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yasin.vcardly.core.database.entity.TagEntity
import com.yasin.vcardly.core.database.entity.TagWithCountRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query(
        """
        SELECT t.*, COUNT(ct.contact_id) AS contact_count
        FROM tags t LEFT JOIN contact_tags ct ON ct.tag_id = t.id
        GROUP BY t.id
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun observeAllWithCounts(): Flow<List<TagWithCountRow>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    /** Returns -1 when the (case-insensitive) name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    /** Returns 0 rows when the new name collides with another tag. */
    @Query("UPDATE OR IGNORE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String): Int

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)
}
