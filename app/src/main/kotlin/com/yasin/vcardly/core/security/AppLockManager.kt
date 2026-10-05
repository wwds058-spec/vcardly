package com.yasin.vcardly.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides WHEN the app is locked. Pure logic over a monotonic clock ([elapsedRealtime], i.e. SystemClock.elapsedRealtime), so
 * changing the phone's date/time can never be used to dodge auto-lock.
 *
 *  - starts locked (a cold start always needs authentication when app lock is on)
 *  - leaving the app starts a timer; coming back after the timeout locks it
 *  - a clock that appears to run backwards (reboot, bug) is treated as "timed out"
 *  - whether app lock is turned on at all is the caller's business: this only tracks the state
 */
class AppLockManager(private val elapsedRealtime: () -> Long) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    /** The user authenticated (or just turned app lock on after authenticating). */
    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    /** The app is no longer visible. Ignored while already locked. */
    fun onBackgrounded() {
        if (!_locked.value && backgroundedAt == null) backgroundedAt = elapsedRealtime()
    }

    /** The app is visible again. [timeoutMillis] is the user's auto-lock setting. */
    fun onForegrounded(appLockEnabled: Boolean, timeoutMillis: Long) {
        val since = backgroundedAt
        backgroundedAt = null
        if (!appLockEnabled || _locked.value || since == null) return
        val away = elapsedRealtime() - since
        if (away < 0 || away >= timeoutMillis) _locked.value = true
    }

    /** Lock right now (e.g. a "lock now" action). */
    fun lock() {
        _locked.value = true
        backgroundedAt = null
    }
}

/** Auto-lock delays offered in Settings, in seconds. The shortest is 15 s so file pickers and permission dialogs don't trigger a re-lock. */
object AutoLockOptions {
    val seconds: List<Int> = listOf(15, 60, 300, 900)
    const val DEFAULT_SECONDS = 60
}
