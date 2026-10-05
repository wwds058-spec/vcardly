package com.yasin.vcardly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.security.AppLockManager
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.domain.repository.PreferencesRepository
import com.yasin.vcardly.domain.repository.SecuritySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val lockManager: AppLockManager,
) : ViewModel() {
    /** null until DataStore has loaded; the splash screen is held until then to avoid a theme flash. */
    val themeMode: StateFlow<ThemeMode?> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null until loaded; decides the start destination so onboarding never flashes for returning users. */
    val onboardingCompleted: StateFlow<Boolean?> = preferences.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null until loaded. The splash stays up until it is known, so protected content can never flash before the lock. */
    val security: StateFlow<SecuritySettings?> = preferences.securitySettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val locked: StateFlow<Boolean> = lockManager.locked

    private val _lockWasRemoved = MutableStateFlow(false)
    /** True after app lock was switched off because the device no longer has any screen lock / biometrics. */
    val lockWasRemoved: StateFlow<Boolean> = _lockWasRemoved.asStateFlow()

    fun onUnlocked() = lockManager.unlock()

    fun onBackgrounded() = lockManager.onBackgrounded()

    fun onForegrounded() {
        val s = security.value ?: return
        lockManager.onForegrounded(s.appLockEnabled, s.autoLockSeconds * 1000L)
    }

    /**
     * The device has no way to authenticate any more (screen lock removed, biometrics deleted). Staying locked would lock the
     * user out of their own data for good, so app lock is turned off and the user is told.
     */
    fun onDeviceAuthMissing() {
        viewModelScope.launch {
            preferences.setAppLockEnabled(false)
            lockManager.unlock()
            _lockWasRemoved.value = true
        }
    }

    fun dismissLockRemovedNotice() { _lockWasRemoved.value = false }
}
