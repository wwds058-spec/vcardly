package com.yasin.vcardly.di

import com.yasin.vcardly.core.datastore.MyCardRepositoryImpl
import com.yasin.vcardly.core.datastore.PreferencesRepositoryImpl
import com.yasin.vcardly.data.repository.CategoryRepositoryImpl
import com.yasin.vcardly.data.repository.ContactRepositoryImpl
import com.yasin.vcardly.data.repository.FollowUpRepositoryImpl
import com.yasin.vcardly.data.repository.TagRepositoryImpl
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.repository.MyCardRepository
import com.yasin.vcardly.domain.repository.PreferencesRepository
import com.yasin.vcardly.domain.repository.TagRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun contacts(impl: ContactRepositoryImpl): ContactRepository
    @Binds abstract fun categories(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds abstract fun tags(impl: TagRepositoryImpl): TagRepository
    @Binds abstract fun followUps(impl: FollowUpRepositoryImpl): FollowUpRepository
    @Binds abstract fun billing(impl: com.yasin.vcardly.core.billing.PlayBillingRepository): com.yasin.vcardly.core.billing.BillingRepository
    @Binds abstract fun myCard(impl: MyCardRepositoryImpl): MyCardRepository
    @Binds abstract fun preferences(impl: PreferencesRepositoryImpl): PreferencesRepository
}
