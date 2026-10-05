package com.yasin.vcardly.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yasin.vcardly.core.database.dao.CategoryDao
import com.yasin.vcardly.core.database.dao.ContactDao
import com.yasin.vcardly.core.database.dao.FollowUpDao
import com.yasin.vcardly.core.database.dao.TagDao
import com.yasin.vcardly.core.database.entity.CategoryEntity
import com.yasin.vcardly.core.database.entity.ContactEntity
import com.yasin.vcardly.core.database.entity.ContactTagCrossRef
import com.yasin.vcardly.core.database.entity.FollowUpEntity
import com.yasin.vcardly.core.database.entity.TagEntity
import com.yasin.vcardly.domain.model.SystemCategory

@Database(
    entities = [
        ContactEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        ContactTagCrossRef::class,
        FollowUpEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VCardlyDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun followUpDao(): FollowUpDao

    companion object {
        const val NAME = "vcardly.db"
    }
}

/**
 * Seeds the system categories on first creation. Names are left blank on purpose; the UI
 * shows a localized name for rows with a system_key.
 */
object SystemCategorySeeder : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        SystemCategory.entries.forEachIndexed { index, category ->
            db.execSQL(
                "INSERT INTO categories (name, color_argb, system_key, sort_order, created_at) VALUES ('', ?, ?, ?, ?)",
                arrayOf<Any?>(category.colorArgb, category.key, index, now),
            )
        }
    }
}
