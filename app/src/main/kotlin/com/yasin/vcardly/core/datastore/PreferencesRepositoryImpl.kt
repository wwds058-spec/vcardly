package com.yasin.vcardly.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yasin.vcardly.core.security.AutoLockOptions
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.domain.repository.SecuritySettings
import com.yasin.vcardly.domain.repository.PreferencesRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val themeMode: Flow<ThemeMode> = prefs
        .map { p -> ThemeMode.entries.firstOrNull { it.name == p[THEME_MODE] } ?: ThemeMode.SYSTEM }
        .distinctUntilChanged()

    override val onboardingCompleted: Flow<Boolean> = prefs
        .map { it[ONBOARDING_COMPLETED] ?: false }
        .distinctUntilChanged()

    override val securitySettings: Flow<SecuritySettings> = prefs
        .map { p ->
            SecuritySettings(
                appLockEnabled = p[APP_LOCK] ?: false,
                autoLockSeconds = (p[AUTO_LOCK_SECONDS] ?: AutoLockOptions.DEFAULT_SECONDS).takeIf { it in AutoLockOptions.seconds } ?: AutoLockOptions.DEFAULT_SECONDS,
                secureScreen = p[SECURE_SCREEN] ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun setAppLockEnabled(enabled: Boolean) { dataStore.edit { it[APP_LOCK] = enabled } }
    override suspend fun setAutoLockSeconds(seconds: Int) { dataStore.edit { it[AUTO_LOCK_SECONDS] = seconds } }
    override suspend fun setSecureScreen(enabled: Boolean) { dataStore.edit { it[SECURE_SCREEN] = enabled } }

    override val proCached: Flow<Boolean> = prefs.map { it[PRO_CACHED] ?: false }.distinctUntilChanged()
    override suspend fun setProCached(pro: Boolean) { dataStore.edit { it[PRO_CACHED] = pro } }

    override val lastBackupAt: Flow<Long?> = prefs.map { it[LAST_BACKUP_AT] }.distinctUntilChanged()

    override suspend fun setLastBackupAt(millis: Long) {
        dataStore.edit { it[LAST_BACKUP_AT] = millis }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val AUTO_LOCK_SECONDS = intPreferencesKey("auto_lock_seconds")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val PRO_CACHED = booleanPreferencesKey("pro_cached")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }
}
