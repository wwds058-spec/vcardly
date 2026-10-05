package com.yasin.vcardly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yasin.vcardly.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity): Int

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    /** Contacts fall back to uncategorised through the FK (ON DELETE SET NULL). */
    @Query("DELETE FROM categories WHERE id = :id AND system_key IS NULL")
    suspend fun deleteCustom(id: Long): Int
}
