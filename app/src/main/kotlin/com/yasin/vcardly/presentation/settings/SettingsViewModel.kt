package com.yasin.vcardly.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val lockManager: com.yasin.vcardly.core.security.AppLockManager,
    private val eraser: com.yasin.vcardly.core.backup.DataEraser,
) : ViewModel() {
    val security: StateFlow<com.yasin.vcardly.domain.repository.SecuritySettings> = preferences.securitySettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.yasin.vcardly.domain.repository.SecuritySettings())

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /** Call only after the user has just authenticated, so the lock does not immediately ask again. */
    fun enableAppLock() {
        viewModelScope.launch {
            preferences.setAppLockEnabled(true)
            lockManager.unlock()
        }
    }

    fun disableAppLock() { viewModelScope.launch { preferences.setAppLockEnabled(false) } }
    fun setAutoLockSeconds(seconds: Int) { viewModelScope.launch { preferences.setAutoLockSeconds(seconds) } }
    fun setSecureScreen(enabled: Boolean) { viewModelScope.launch { preferences.setSecureScreen(enabled) } }

    fun eraseAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            eraser.eraseAll()
            onDone()
        }
    }
}
