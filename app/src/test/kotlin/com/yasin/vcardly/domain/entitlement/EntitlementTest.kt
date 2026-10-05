package com.yasin.vcardly.domain.entitlement

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementTest {
    private val free = EntitlementState()
    private val pro = EntitlementState(isPro = true)
    private val ids = setOf("pro_lifetime", "pro_yearly")

    private fun p(vararg product: String, state: PurchaseStateKind = PurchaseStateKind.PURCHASED, ack: Boolean = true, token: String = "t") =
        PurchaseSnapshot(product.toList(), state, ack, token)

    // ---- policy

    @Test fun everyFeatureIsProOnly_andFreeIsNeverAllowed() {
        Feature.entries.forEach {
            assertFalse(it.name, EntitlementPolicy.isAllowed(it, free))
            assertTrue(it.name, EntitlementPolicy.isAllowed(it, pro))
        }
    }

    @Test fun scanQuota_freeIsLimited_proIsNot() {
        val a = EntitlementPolicy.scanAllowance(free, 24) as ScanAllowance.Limited
        assertTrue(a.isAllowed); assertEquals(1, a.remaining)
        val full = EntitlementPolicy.scanAllowance(free, 25) as ScanAllowance.Limited
        assertFalse(full.isAllowed); assertEquals(0, full.remaining)
        assertEquals(0, (EntitlementPolicy.scanAllowance(free, 999) as ScanAllowance.Limited).remaining) // never negative
        assertEquals(ScanAllowance.Unlimited, EntitlementPolicy.scanAllowance(pro, 999))
    }

    @Test fun monthStart_isFirstOfMonthInTheDeviceZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 2026-05-31T20:00Z is already June 1st 05:00 in Tokyo
        val start = EntitlementPolicy.monthStart(Instant.parse("2026-05-31T20:00:00Z"), tokyo)
        assertEquals(Instant.parse("2026-05-31T15:00:00Z").toEpochMilli(), start)
        assertEquals(Instant.parse("2026-06-01T00:00:00Z").toEpochMilli(), EntitlementPolicy.monthStart(Instant.parse("2026-06-15T12:00:00Z"), ZoneId.of("UTC")))
    }

    @Test fun reconcile_trustsPlayWhenItAnswers_andTheCacheWhenItCannot() {
        assertTrue(EntitlementPolicy.reconcile(false, QueryOutcome.Success(true)))
        assertFalse(EntitlementPolicy.reconcile(true, QueryOutcome.Success(false)))   // refund / expiry is honoured
        assertTrue(EntitlementPolicy.reconcile(true, QueryOutcome.Failed))             // offline: a paying user stays Pro
        assertFalse(EntitlementPolicy.reconcile(false, QueryOutcome.Failed))
    }

    @Test fun ads_onlyForFreeUsers_whenEnabled_consented_andVisible() {
        assertTrue(EntitlementPolicy.shouldShowAds(free, adsEnabled = true, canRequestAds = true, appLocked = false))
        assertFalse(EntitlementPolicy.shouldShowAds(pro, true, true, false))
        assertFalse(EntitlementPolicy.shouldShowAds(free, false, true, false))   // not configured in this build
        assertFalse(EntitlementPolicy.shouldShowAds(free, true, false, false))   // no consent yet
        assertFalse(EntitlementPolicy.shouldShowAds(free, true, true, true))     // locked: hidden screen must not count impressions
    }

    @Test fun adPlacements_areAnAllowList_thatChangesOnlyOnPurpose() {
        // If you add a placement, check that its screen shows no contact data, then update this list.
        assertEquals(listOf("HOME"), AdPlacement.entries.map { it.name })
    }

    // ---- purchases

    @Test fun completedProPurchase_entitles_andIsAcknowledgedOnce() {
        val r = PurchaseInterpreter.interpret(listOf(p("pro_yearly", ack = false, token = "abc")), ids)
        assertTrue(r.entitled); assertFalse(r.pending); assertEquals(listOf("abc"), r.tokensToAcknowledge)
        assertTrue(PurchaseInterpreter.interpret(listOf(p("pro_yearly", ack = true)), ids).tokensToAcknowledge.isEmpty())
    }

    @Test fun pendingPurchase_doesNotUnlockAnything() {
        val r = PurchaseInterpreter.interpret(listOf(p("pro_lifetime", state = PurchaseStateKind.PENDING, ack = false)), ids)
        assertFalse(r.entitled); assertTrue(r.pending); assertTrue(r.tokensToAcknowledge.isEmpty())
    }

    @Test fun otherProductsAndEmptyListsAreIgnored() {
        assertFalse(PurchaseInterpreter.interpret(listOf(p("some_other_item")), ids).entitled)
        assertFalse(PurchaseInterpreter.interpret(emptyList(), ids).entitled)
        assertFalse(PurchaseInterpreter.interpret(listOf(p("pro_yearly", state = PurchaseStateKind.UNSPECIFIED)), ids).entitled)
    }

    @Test fun pendingPlusCompleted_isEntitled_withoutShowingPending() {
        val r = PurchaseInterpreter.interpret(listOf(p("pro_lifetime", token = "done"), p("pro_yearly", state = PurchaseStateKind.PENDING)), ids)
        assertTrue(r.entitled); assertTrue(r.pending)
    }
}
