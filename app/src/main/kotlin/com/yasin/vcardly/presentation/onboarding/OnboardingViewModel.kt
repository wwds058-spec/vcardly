package com.yasin.vcardly.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
) : ViewModel() {
    /** Persists completion first, then calls [onDone] so the next launch skips onboarding. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            onDone()
        }
    }
}
