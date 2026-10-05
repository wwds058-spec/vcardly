package com.yasin.vcardly.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockManagerTest {
    private var now = 1_000_000L
    private val manager = AppLockManager { now }
    private val timeout = 60_000L

    @Test fun coldStart_isLocked_untilUnlocked() {
        assertTrue(manager.locked.value)
        manager.unlock()
        assertFalse(manager.locked.value)
    }

    @Test fun briefAbsence_staysUnlocked() {
        manager.unlock()
        manager.onBackgrounded(); now += 59_999
        manager.onForegrounded(true, timeout)
        assertFalse(manager.locked.value)
    }

    @Test fun absenceAtOrBeyondTimeout_locks() {
        manager.unlock()
        manager.onBackgrounded(); now += 60_000
        manager.onForegrounded(true, timeout)
        assertTrue(manager.locked.value)
    }

    @Test fun clockRunningBackwards_isTreatedAsTimedOut() {
        manager.unlock()
        manager.onBackgrounded(); now -= 5_000
        manager.onForegrounded(true, timeout)
        assertTrue(manager.locked.value)
    }

    @Test fun appLockDisabled_neverChangesState() {
        manager.unlock()
        manager.onBackgrounded(); now += 10_000_000
        manager.onForegrounded(false, timeout)
        assertFalse(manager.locked.value)
    }

    @Test fun repeatedBackgroundCalls_keepTheFirstTimestamp() {
        manager.unlock()
        manager.onBackgrounded(); now += 40_000
        manager.onBackgrounded(); now += 40_000     // the second call must not restart the timer
        manager.onForegrounded(true, timeout)
        assertTrue(manager.locked.value)
    }

    @Test fun foregroundWithoutBackground_doesNothing() {
        manager.unlock()
        manager.onForegrounded(true, timeout)
        assertFalse(manager.locked.value)
    }

    @Test fun unlockResetsTheTimer_andLockIsImmediate() {
        manager.unlock()
        manager.onBackgrounded(); now += 30_000
        manager.unlock(); now += 100_000
        manager.onForegrounded(true, timeout)       // no stale timestamp survives an unlock
        assertFalse(manager.locked.value)
        manager.lock()
        assertTrue(manager.locked.value)
    }

    @Test fun backgroundWhileLocked_isIgnored() {
        manager.onBackgrounded(); now += 5_000_000
        manager.unlock()
        manager.onForegrounded(true, timeout)
        assertFalse(manager.locked.value)
    }

    @Test fun options_areSortedAndIncludeTheDefault() {
        assertTrue(AutoLockOptions.seconds == AutoLockOptions.seconds.sorted())
        assertTrue(AutoLockOptions.DEFAULT_SECONDS in AutoLockOptions.seconds)
    }
}
