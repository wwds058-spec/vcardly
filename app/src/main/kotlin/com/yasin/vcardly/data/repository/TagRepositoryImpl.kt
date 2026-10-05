package com.yasin.vcardly.data.repository

import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.core.database.entity.TagEntity
import com.yasin.vcardly.core.database.mapper.toDomain
import com.yasin.vcardly.domain.model.Tag
import com.yasin.vcardly.domain.model.TagWithCount
import com.yasin.vcardly.domain.repository.TagRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TagRepositoryImpl @Inject constructor(
    db: VCardlyDatabase,
    private val clock: Clock,
) : TagRepository {
    private val dao = db.tagDao()

    override fun observeAll(): Flow<List<TagWithCount>> =
        dao.observeAllWithCounts().map { rows -> rows.map { it.toDomain() } }

    override suspend fun findOrCreate(name: String): Tag? {
        val clean = name.trim().removePrefix("#").trim()
        if (clean.isEmpty()) return null
        dao.findByName(clean)?.let { return it.toDomain() }
        dao.insert(TagEntity(name = clean, createdAt = clock.millis()))
        // Re-read: covers the race where another insert won (IGNORE returns -1).
        return dao.findByName(clean)?.toDomain()
    }

    override suspend fun rename(id: Long, name: String): Boolean {
        val clean = name.trim().removePrefix("#").trim()
        return clean.isNotEmpty() && dao.rename(id, clean) > 0
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
