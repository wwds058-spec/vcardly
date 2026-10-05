package com.yasin.vcardly.domain.entitlement

import java.time.Instant
import java.time.ZoneId

/**
 * Everything that is behind VCardly Pro. This list and [EntitlementPolicy] are the ONLY place the free/Pro split is decided,
 * so changing the offer is a one-file change.
 *
 * Deliberately NOT paywalled: your own data and your safety. Contacts, vCard / CSV export, backup and restore, app lock,
 * encrypted backups, reminders and erase-all stay free for everyone.
 */
enum class Feature { PDF_REPORT, EXCEL_EXPORT, UNLIMITED_SCANS, AD_FREE }

/**
 * Where a banner may appear. Ads must never sit on a screen that shows an individual's details (names, companies, phone
 * numbers, emails, notes, tags, card images) or on the scanner, forms, lock, privacy or backup screens. Home qualifies because
 * it only shows aggregate counts and category labels. Add a placement only after checking that; the allow-list test fails on
 * purpose when this enum changes. The ads SDK is never given any app data in any case.
 */
enum class AdPlacement { HOME }

data class EntitlementState(val isPro: Boolean = false, val purchasePending: Boolean = false)

sealed interface ScanAllowance {
    data object Unlimited : ScanAllowance
    data class Limited(val used: Int, val limit: Int) : ScanAllowance {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val isAllowed: Boolean get() = used < limit
    }
}

/** Result of asking Google Play what the user owns. [Failed] means "could not find out" (offline, Play unavailable). */
sealed interface QueryOutcome {
    data class Success(val entitled: Boolean) : QueryOutcome
    data object Failed : QueryOutcome
}

object EntitlementPolicy {
    const val FREE_SCANS_PER_MONTH = 25

    fun isAllowed(@Suppress("UNUSED_PARAMETER") feature: Feature, state: EntitlementState): Boolean = state.isPro

    fun scanAllowance(state: EntitlementState, scannedThisMonth: Int): ScanAllowance =
        if (state.isPro) ScanAllowance.Unlimited else ScanAllowance.Limited(scannedThisMonth, FREE_SCANS_PER_MONTH)

    /** Start of the current calendar month in the device zone: the free scan quota resets here. */
    fun monthStart(now: Instant, zone: ZoneId): Long =
        now.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Offline-first: a successful Play answer is the truth (and replaces the cache, so refunds and expiries take effect);
     * when Play cannot be reached the last known answer is kept, so a paying user is not downgraded just because they are offline.
     */
    fun reconcile(cachedPro: Boolean, outcome: QueryOutcome): Boolean = when (outcome) {
        is QueryOutcome.Success -> outcome.entitled
        QueryOutcome.Failed -> cachedPro
    }

    fun shouldShowAds(state: EntitlementState, adsEnabled: Boolean, canRequestAds: Boolean, appLocked: Boolean): Boolean =
        !state.isPro && adsEnabled && canRequestAds && !appLocked
}
