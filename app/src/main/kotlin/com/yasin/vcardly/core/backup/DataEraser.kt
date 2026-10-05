package com.yasin.vcardly.core.backup

import androidx.room.withTransaction
import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.core.database.entity.CategoryEntity
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.domain.model.MyCard
import com.yasin.vcardly.domain.model.SystemCategory
import com.yasin.vcardly.domain.repository.MyCardRepository
import com.yasin.vcardly.domain.reminder.ReminderScheduler
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** "Erase all my data": contacts, follow-ups, tags, categories, card images, my card. Settings are kept. */
@Singleton
class DataEraser @Inject constructor(
    private val db: VCardlyDatabase,
    private val images: CardImageStore,
    private val myCard: MyCardRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend fun eraseAll() {
        val followUpIds = db.followUpDao().getAll().map { it.id }
        db.withTransaction {
            db.followUpDao().deleteAll()
            db.contactDao().deleteAllTagLinks()
            db.contactDao().deleteAll()
            db.tagDao().deleteAll()
            db.categoryDao().deleteAll()
            // The app expects the built-in categories to exist.
            SystemCategory.entries.forEachIndexed { index, c ->
                db.categoryDao().insert(CategoryEntity(0, "", c.colorArgb, c.key, index, clock.millis()))
            }
        }
        images.deleteAllStored()
        images.clearScanCache()
        images.clearRestoreCache()
        myCard.save(MyCard())
        followUpIds.forEach(scheduler::cancel) // also removes any visible reminder notification
    }
}
