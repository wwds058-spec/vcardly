package com.yasin.vcardly.di

import android.content.Context
import androidx.room.Room
import com.yasin.vcardly.core.database.ALL_MIGRATIONS
import com.yasin.vcardly.core.database.SystemCategorySeeder
import com.yasin.vcardly.core.database.VCardlyDatabase
import com.yasin.vcardly.core.database.dao.CategoryDao
import com.yasin.vcardly.core.database.dao.ContactDao
import com.yasin.vcardly.core.database.dao.FollowUpDao
import com.yasin.vcardly.core.database.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VCardlyDatabase =
        Room.databaseBuilder(context, VCardlyDatabase::class.java, VCardlyDatabase.NAME)
            .addCallback(SystemCategorySeeder)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides fun provideContactDao(db: VCardlyDatabase): ContactDao = db.contactDao()
    @Provides fun provideCategoryDao(db: VCardlyDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTagDao(db: VCardlyDatabase): TagDao = db.tagDao()
    @Provides fun provideFollowUpDao(db: VCardlyDatabase): FollowUpDao = db.followUpDao()
}
