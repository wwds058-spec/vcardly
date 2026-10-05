package com.yasin.vcardly.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.yasin.vcardly.BuildConfig
import com.yasin.vcardly.core.common.AppDispatchers
import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.core.database.entity.CategoryEntity
import com.yasin.vcardly.core.database.entity.ContactEntity
import com.yasin.vcardly.core.database.entity.ContactTagCrossRef
import com.yasin.vcardly.core.database.entity.FollowUpEntity
import com.yasin.vcardly.core.database.entity.TagEntity
import com.yasin.vcardly.core.database.mapper.toDomain
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.domain.backup.BCategory
import com.yasin.vcardly.domain.backup.BContact
import com.yasin.vcardly.domain.backup.BFollowUp
import com.yasin.vcardly.domain.backup.BMyCard
import com.yasin.vcardly.domain.backup.BTag
import com.yasin.vcardly.domain.backup.BackupArchive
import com.yasin.vcardly.domain.backup.BackupContents
import com.yasin.vcardly.domain.backup.BackupCounts
import com.yasin.vcardly.domain.backup.BackupData
import com.yasin.vcardly.domain.backup.BackupException
import com.yasin.vcardly.domain.backup.BackupImage
import com.yasin.vcardly.domain.backup.BackupManifest
import com.yasin.vcardly.domain.backup.CategoryTarget
import com.yasin.vcardly.domain.backup.ExistingCategory
import com.yasin.vcardly.domain.backup.RestorePlanner
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.model.MyCard
import com.yasin.vcardly.domain.model.SystemCategory
import com.yasin.vcardly.domain.reminder.ReminderScheduler
import com.yasin.vcardly.domain.repository.MyCardRepository
import com.yasin.vcardly.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class RestoreMode { MERGE, REPLACE }

enum class BackupFailure { NOT_A_BACKUP, NEWER_VERSION, NEEDS_PASSWORD, WRONG_PASSWORD, CORRUPT, TOO_LARGE, IO }

sealed interface BackupResult<out T> {
    data class Success<T>(val value: T) : BackupResult<T>
    data class Failure(val reason: BackupFailure) : BackupResult<Nothing>
}

data class RestoreSummary(val contactsAdded: Int, val duplicatesSkipped: Int, val followUps: Int, val images: Int)

/**
 * Creates and restores `vcardly-backup-v1` files. Nothing here logs, and nothing leaves the device: the user picks the file
 * location with the system picker. Restore is staged: the whole file is read and verified into a cache folder first, so a
 * damaged or wrong-password file changes nothing; the database part then runs in one transaction.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: VCardlyDatabase,
    private val images: CardImageStore,
    private val myCard: MyCardRepository,
    private val prefs: PreferencesRepository,
    private val scheduler: ReminderScheduler,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
) {
    // ---------------------------------------------------------------- create

    suspend fun create(uri: Uri, password: CharArray?): BackupResult<BackupCounts> = withContext(dispatchers.io) {
        try {
            val snapshot = snapshot()
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext BackupResult.Failure(BackupFailure.IO)
            BufferedOutputStream(out).use { BackupArchive.write(snapshot.data, snapshot.images, BuildConfig.VERSION_NAME, clock.millis(), it, password) }
            prefs.setLastBackupAt(clock.millis())
            BackupResult.Success(
                BackupCounts(snapshot.data.contacts.size, snapshot.data.categories.size, snapshot.data.tags.size, snapshot.data.followUps.size, snapshot.images.size),
            )
        } catch (_: IOException) {
            BackupResult.Failure(BackupFailure.IO)
        } catch (_: SecurityException) {
            BackupResult.Failure(BackupFailure.IO)
        }
    }

    private class Snapshot(val data: BackupData, val images: List<BackupImage>)

    private class Rows(
        val contacts: List<ContactEntity>, val categories: List<CategoryEntity>, val tags: List<TagEntity>,
        val links: List<ContactTagCrossRef>, val followUps: List<FollowUpEntity>,
    )

    private suspend fun snapshot(): Snapshot {
        // One transaction = one consistent view of all tables.
        val rows = db.withTransaction {
            Rows(db.contactDao().getAll(), db.categoryDao().getAll(), db.tagDao().getAll(), db.contactDao().getAllTagLinks(), db.followUpDao().getAll())
        }
        val contacts = rows.contacts; val categories = rows.categories; val tags = rows.tags
        val links = rows.links; val followUps = rows.followUps
        val tagsByContact = links.groupBy({ it.contactId }, { it.tagId })

        // Only images that really exist are referenced, so a backup never points at a missing file.
        val present = (contacts.flatMap { listOfNotNull(it.frontImagePath, it.backImagePath) }).distinct()
            .filter { path -> images.openStored(path)?.use { true } == true }
        fun imageName(path: String?) = path?.takeIf { it in present }?.substringAfterLast('/')

        val data = BackupData(
            categories = categories.map { BCategory(it.id, it.name, it.colorArgb, it.systemKey, it.sortOrder, it.createdAt) },
            tags = tags.map { BTag(it.id, it.name, it.colorArgb, it.createdAt) },
            contacts = contacts.map { c ->
                BContact(
                    id = c.id, fullName = c.fullName, jobTitle = c.jobTitle, company = c.company, phone = c.phone, phoneAlt = c.phoneAlt,
                    email = c.email, emailAlt = c.emailAlt, website = c.website, address = c.address, notes = c.notes,
                    categoryId = c.categoryId, isFavorite = c.isFavorite, frontImage = imageName(c.frontImagePath), backImage = imageName(c.backImagePath),
                    source = c.source.name, createdAt = c.createdAt, updatedAt = c.updatedAt, tagIds = tagsByContact[c.id].orEmpty(),
                )
            },
            followUps = followUps.map { f ->
                BFollowUp(
                    f.id, f.contactId, f.title, f.type.name, f.status.name, f.notes, f.dueAt, f.reminderEnabled, f.reminderOffsetMinutes,
                    f.completedAt, f.notifiedAt, f.createdAt, f.updatedAt,
                )
            },
            myCard = myCardOrNull(),
        )
        val files = present.map { path -> BackupImage(path.substringAfterLast('/')) { checkNotNull(images.openStored(path)) } }
        return Snapshot(data, files)
    }

    private suspend fun myCardOrNull(): BMyCard? {
        val c = currentMyCard()
        return if (c.isEmpty) null else BMyCard(c.fullName, c.jobTitle, c.company, c.phone, c.phoneAlt, c.email, c.emailAlt, c.website, c.address)
    }

    private suspend fun currentMyCard(): MyCard = myCard.card.first()

    // --------------------------------------------------------------- inspect

    suspend fun inspect(uri: Uri, password: CharArray?): BackupResult<BackupManifest> = withContext(dispatchers.io) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext BackupResult.Failure(BackupFailure.IO)
            BufferedInputStream(input).use { BackupResult.Success(BackupArchive.inspect(it, password)) }
        } catch (e: BackupException) {
            BackupResult.Failure(e.toFailure())
        } catch (_: IOException) {
            BackupResult.Failure(BackupFailure.IO)
        } catch (_: SecurityException) {
            BackupResult.Failure(BackupFailure.IO)
        }
    }

    // --------------------------------------------------------------- restore

    suspend fun restore(uri: Uri, password: CharArray?, mode: RestoreMode): BackupResult<RestoreSummary> = withContext(dispatchers.io) {
        val stage = images.newRestoreDir()
        try {
            val contents = readInto(uri, password, stage)
            val summary = when (mode) {
                RestoreMode.REPLACE -> replaceAll(contents, stage)
                RestoreMode.MERGE -> merge(contents, stage)
            }
            runCatching { scheduler.rescheduleAll() } // alarms for the restored follow-ups
            BackupResult.Success(summary)
        } catch (e: BackupException) {
            BackupResult.Failure(e.toFailure())
        } catch (_: IOException) {
            BackupResult.Failure(BackupFailure.IO)
        } catch (_: SecurityException) {
            BackupResult.Failure(BackupFailure.IO)
        } catch (_: android.database.SQLException) {
            BackupResult.Failure(BackupFailure.CORRUPT)
        } finally {
            images.clearRestoreCache()
        }
    }

    private fun readInto(uri: Uri, password: CharArray?, stage: File): BackupContents {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open")
        return BufferedInputStream(input).use { BackupArchive.read(it, password) { name -> File(stage, name).outputStream().buffered() } }
    }

    private fun staged(stage: File, name: String?): File? = name?.let { File(stage, it) }?.takeIf { it.isFile }

    private suspend fun replaceAll(c: BackupContents, stage: File): RestoreSummary {
        val d = c.data
        val categoryIds = d.categories.map { it.id }.toSet()
        val tagIds = d.tags.map { it.id }.toSet()
        val contactIds = d.contacts.map { it.id }.toSet()
        val hasFront = { n: String? -> staged(stage, n) != null }

        db.withTransaction {
            db.followUpDao().deleteAll(); db.contactDao().deleteAllTagLinks(); db.contactDao().deleteAll()
            db.tagDao().deleteAll(); db.categoryDao().deleteAll()

            d.categories.forEach { db.categoryDao().insert(CategoryEntity(it.id, it.name.cap(80), it.colorArgb, it.systemKey, it.sortOrder, it.createdAt)) }
            ensureSystemCategories()
            d.tags.forEach { db.tagDao().insert(TagEntity(it.id, it.name.cap(80), it.colorArgb, it.createdAt)) }
            d.contacts.forEach { b ->
                db.contactDao().insert(
                    contactEntity(
                        b, id = b.id, categoryId = b.categoryId?.takeIf { it in categoryIds },
                        front = b.frontImage?.takeIf(hasFront)?.let { "cards/$it" }, back = b.backImage?.takeIf(hasFront)?.let { "cards/$it" },
                    ),
                )
                db.contactDao().insertTagLinks(b.tagIds.filter { it in tagIds }.distinct().map { ContactTagCrossRef(b.id, it) })
            }
            d.followUps.filter { it.contactId in contactIds }.forEach { db.followUpDao().insert(followUpEntity(it, id = it.id, contactId = it.contactId)) }
        }
        // Database committed: now swap the image files. (A failure here leaves contacts without a picture, never without data.)
        val installed = images.replaceAllFromRestore(stage)
        d.myCard?.let { myCard.save(it.toDomain()) }
        return RestoreSummary(d.contacts.size, 0, d.followUps.count { it.contactId in contactIds }, if (installed) stage.listFiles()?.size ?: 0 else 0)
    }

    private suspend fun merge(c: BackupContents, stage: File): RestoreSummary {
        val existingContacts = db.contactDao().getAll().map { it.toDomain() }
        val existingCategories = db.categoryDao().getAll().map { ExistingCategory(it.id, it.name, it.systemKey) }
        val plan = RestorePlanner.planMerge(c.data, existingContacts, existingCategories)

        // Copy images into permanent storage first; if the transaction fails they are removed again.
        val installedPaths = mutableListOf<String>()
        suspend fun install(name: String?): String? = staged(stage, name)?.let { images.installFromRestore(it) }?.also { installedPaths += it }
        val prepared = plan.contacts.map { p -> Triple(p, install(p.contact.frontImage), install(p.contact.backImage)) }

        var followUpCount = 0
        try {
            db.withTransaction {
                val createdCategory = mutableMapOf<Long, Long>()
                prepared.forEach { (p, front, back) ->
                    val categoryId = when (val t = p.category) {
                        CategoryTarget.None -> null
                        is CategoryTarget.Existing -> t.id
                        is CategoryTarget.Create -> createdCategory.getOrPut(t.category.id) {
                            db.categoryDao().insert(
                                CategoryEntity(0, t.category.name.cap(80), t.category.colorArgb, null, db.categoryDao().nextSortOrder(), clock.millis()),
                            )
                        }
                    }
                    val newId = db.contactDao().insert(contactEntity(p.contact, id = 0, categoryId = categoryId, front = front, back = back))
                    val tagIds = p.tagNames.map { name ->
                        db.tagDao().findByName(name)?.id ?: db.tagDao().insert(TagEntity(0, name.cap(80), null, clock.millis())).let { db.tagDao().findByName(name)!!.id }
                    }
                    db.contactDao().insertTagLinks(tagIds.distinct().map { ContactTagCrossRef(newId, it) })
                    p.followUps.forEach { db.followUpDao().insert(followUpEntity(it, id = 0, contactId = newId)); followUpCount++ }
                }
            }
        } catch (e: Exception) {
            installedPaths.forEach { images.delete(it) }
            throw e
        }
        c.data.myCard?.let { if (currentMyCard().isEmpty) myCard.save(it.toDomain()) }
        return RestoreSummary(plan.contacts.size, plan.skippedDuplicates, followUpCount, installedPaths.size)
    }

    /** The app expects all seeded categories to exist; a backup that lacks one must not leave the app without it. */
    private suspend fun ensureSystemCategories() {
        val have = db.categoryDao().getAll().mapNotNull { it.systemKey }.toSet()
        SystemCategory.entries.filter { it.key !in have }.forEach {
            db.categoryDao().insert(CategoryEntity(0, "", it.colorArgb, it.key, db.categoryDao().nextSortOrder(), clock.millis()))
        }
    }

    // --------------------------------------------------------------- mapping

    private fun contactEntity(b: BContact, id: Long, categoryId: Long?, front: String?, back: String?) = ContactEntity(
        id = id, fullName = b.fullName.cap(200).ifBlank { "?" }, jobTitle = b.jobTitle.cap(300), company = b.company.cap(300),
        phone = b.phone.cap(100), phoneAlt = b.phoneAlt.cap(100), email = b.email.cap(300), emailAlt = b.emailAlt.cap(300),
        website = b.website.cap(500), address = b.address.cap(1000), notes = b.notes.cap(20_000), categoryId = categoryId,
        isFavorite = b.isFavorite, frontImagePath = front, backImagePath = back,
        source = ContactSource.entries.firstOrNull { it.name == b.source } ?: ContactSource.IMPORT,
        createdAt = b.createdAt, updatedAt = b.updatedAt,
    )

    private fun followUpEntity(f: BFollowUp, id: Long, contactId: Long) = FollowUpEntity(
        id = id, contactId = contactId, type = FollowUpType.entries.firstOrNull { it.name == f.type } ?: FollowUpType.OTHER,
        status = FollowUpStatus.entries.firstOrNull { it.name == f.status } ?: FollowUpStatus.PENDING,
        title = f.title.cap(300).ifBlank { "?" }, notes = f.notes.cap(5_000), dueAt = f.dueAt, reminderEnabled = f.reminderEnabled,
        reminderOffsetMinutes = f.reminderOffsetMinutes, completedAt = f.completedAt, notifiedAt = f.notifiedAt,
        createdAt = f.createdAt, updatedAt = f.updatedAt,
    )

    private fun BMyCard.toDomain() = MyCard(fullName.cap(200), jobTitle.cap(300), company.cap(300), phone.cap(100), phoneAlt.cap(100), email.cap(300), emailAlt.cap(300), website.cap(500), address.cap(1000))

    /** Bounds field sizes so a crafted file cannot store megabytes in a name field. */
    private fun String.cap(max: Int) = if (length <= max) this else substring(0, max)

    private fun BackupException.toFailure() = when (this) {
        is BackupException.NotABackup -> BackupFailure.NOT_A_BACKUP
        is BackupException.NewerVersion -> BackupFailure.NEWER_VERSION
        is BackupException.NeedsPassword -> BackupFailure.NEEDS_PASSWORD
        is BackupException.WrongPasswordOrCorrupt -> BackupFailure.WRONG_PASSWORD
        is BackupException.Corrupt -> BackupFailure.CORRUPT
        is BackupException.TooLarge -> BackupFailure.TOO_LARGE
    }
}
