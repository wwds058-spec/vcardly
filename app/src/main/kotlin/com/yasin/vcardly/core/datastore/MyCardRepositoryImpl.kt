package com.yasin.vcardly.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yasin.vcardly.domain.model.MyCard
import com.yasin.vcardly.domain.repository.MyCardRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class MyCardRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : MyCardRepository {
    override val card: Flow<MyCard> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            MyCard(
                fullName = p[NAME].orEmpty(), jobTitle = p[TITLE].orEmpty(), company = p[COMPANY].orEmpty(),
                phone = p[PHONE].orEmpty(), phoneAlt = p[PHONE_ALT].orEmpty(), email = p[EMAIL].orEmpty(),
                emailAlt = p[EMAIL_ALT].orEmpty(), website = p[WEBSITE].orEmpty(), address = p[ADDRESS].orEmpty(),
            )
        }
        .distinctUntilChanged()

    override suspend fun save(card: MyCard) {
        dataStore.edit {
            it[NAME] = card.fullName.trim(); it[TITLE] = card.jobTitle.trim(); it[COMPANY] = card.company.trim()
            it[PHONE] = card.phone.trim(); it[PHONE_ALT] = card.phoneAlt.trim(); it[EMAIL] = card.email.trim()
            it[EMAIL_ALT] = card.emailAlt.trim(); it[WEBSITE] = card.website.trim(); it[ADDRESS] = card.address.trim()
        }
    }

    private companion object {
        val NAME = stringPreferencesKey("mycard_name")
        val TITLE = stringPreferencesKey("mycard_title")
        val COMPANY = stringPreferencesKey("mycard_company")
        val PHONE = stringPreferencesKey("mycard_phone")
        val PHONE_ALT = stringPreferencesKey("mycard_phone_alt")
        val EMAIL = stringPreferencesKey("mycard_email")
        val EMAIL_ALT = stringPreferencesKey("mycard_email_alt")
        val WEBSITE = stringPreferencesKey("mycard_website")
        val ADDRESS = stringPreferencesKey("mycard_address")
    }
}
