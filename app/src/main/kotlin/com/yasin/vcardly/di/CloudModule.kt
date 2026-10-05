package com.yasin.vcardly.di

import com.yasin.vcardly.core.cloud.CloudBackupProvider
import com.yasin.vcardly.core.cloud.UnconfiguredDriveProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudModule {
    /** Swap this for a real Google Drive implementation once docs/GOOGLE_DRIVE_SETUP.md is done. */
    @Provides
    @Singleton
    fun provideCloudBackupProvider(): CloudBackupProvider = UnconfiguredDriveProvider()
}
