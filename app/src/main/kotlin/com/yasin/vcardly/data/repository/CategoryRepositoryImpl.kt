package com.yasin.vcardly.data.repository

import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.core.database.entity.CategoryEntity
import com.yasin.vcardly.core.database.mapper.toDomain
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.repository.CategoryRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    db: VCardlyDatabase,
    private val clock: Clock,
) : CategoryRepository {
    private val dao = db.categoryDao()

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(category: Category): Long {
        if (category.id == 0L) {
            return dao.insert(
                CategoryEntity(
                    name = category.name.trim(),
                    colorArgb = category.colorArgb,
                    sortOrder = dao.nextSortOrder(),
                    createdAt = clock.millis(),
                ),
            )
        }
        val existing = dao.getById(category.id) ?: return -1L
        // System categories keep their key and blank name; only the colour may change.
        val name = if (existing.systemKey != null) existing.name else category.name.trim()
        dao.update(existing.copy(name = name, colorArgb = category.colorArgb))
        return existing.id
    }

    override suspend fun delete(id: Long): Boolean = dao.deleteCustom(id) > 0
}
