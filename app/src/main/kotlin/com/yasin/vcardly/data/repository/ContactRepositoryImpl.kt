package com.yasin.vcardly.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.yasin.vcardly.core.database.ContactQueryBuilder
import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.core.database.entity.ContactTagCrossRef
import com.yasin.vcardly.core.database.mapper.toDomain
import com.yasin.vcardly.core.database.mapper.toEntity
import com.yasin.vcardly.domain.model.CategoryCount
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.ContactStats
import com.yasin.vcardly.domain.repository.ContactRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val db: VCardlyDatabase,
    private val clock: Clock,
) : ContactRepository {
    private val dao = db.contactDao()

    override fun observeContacts(filter: ContactFilter): Flow<List<ContactDetails>> {
        val built = ContactQueryBuilder.build(filter)
        return dao.observeFiltered(SimpleSQLiteQuery(built.sql, built.args.toTypedArray()))
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeContact(id: Long): Flow<ContactDetails?> =
        dao.observeDetails(id).map { it?.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<ContactDetails>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getContact(id: Long): ContactDetails? = dao.getDetails(id)?.toDomain()

    override suspend fun save(contact: Contact, tagIds: Set<Long>): Long = db.withTransaction {
        val now = clock.millis()
        val id = if (contact.id == 0L) {
            dao.insert(contact.toEntity().copy(createdAt = now, updatedAt = now))
        } else {
            dao.update(contact.toEntity().copy(updatedAt = now))
            contact.id
        }
        dao.clearTagLinks(id)
        dao.insertTagLinks(tagIds.map { ContactTagCrossRef(contactId = id, tagId = it) })
        id
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) =
        dao.setFavorite(id, favorite, clock.millis())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun countScannedSince(sinceMillis: Long): Int = dao.countScannedSince(sinceMillis)

    override fun observeStats(addedSince: Long): Flow<ContactStats> = combine(
        dao.observeTotalCount(),
        dao.observeFavoriteCount(),
        dao.observeCountSince(addedSince),
        dao.observeCategoryCounts(),
    ) { total, favorites, added, byCategory ->
        ContactStats(
            total = total,
            favorites = favorites,
            addedSince = added,
            byCategory = byCategory.map { CategoryCount(it.categoryId, it.count) },
        )
    }
}
