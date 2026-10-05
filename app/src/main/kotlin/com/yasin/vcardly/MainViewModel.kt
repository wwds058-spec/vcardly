package com.yasin.vcardly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: PreferencesRepository,
) : ViewModel() {
    /** null until DataStore has loaded; the splash screen is held until then to avoid a theme flash. */
    val themeMode: StateFlow<ThemeMode?> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null until loaded; decides the start destination so onboarding never flashes for returning users. */
    val onboardingCompleted: StateFlow<Boolean?> = preferences.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
