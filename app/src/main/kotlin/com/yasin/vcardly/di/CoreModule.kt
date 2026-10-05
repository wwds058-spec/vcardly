package com.yasin.vcardly.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.yasin.vcardly.core.common.AppDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "vcardly_preferences")

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    @Singleton
    fun provideDispatchers(): AppDispatchers =
        AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)

    /** System clock in the device's current zone; tests inject a fixed clock. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.appDataStore
}
