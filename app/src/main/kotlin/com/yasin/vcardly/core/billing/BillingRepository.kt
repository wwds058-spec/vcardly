package com.yasin.vcardly.core.billing

import android.app.Activity
import com.yasin.vcardly.domain.entitlement.QueryOutcome
import kotlinx.coroutines.flow.StateFlow

enum class ProductKind { LIFETIME, YEARLY }

/** A purchasable Pro product. [price] is Google's already-formatted, localized text: prices are never hard-coded. */
data class ProProduct(val id: String, val kind: ProductKind, val price: String)

sealed interface BillingStatus {
    data object Connecting : BillingStatus
    data object Ready : BillingStatus
    /** Play Store / Play Billing is missing, outdated or disabled on this device. */
    data object Unavailable : BillingStatus
    /** Connected, but Play knows none of our product IDs: the Play Console setup is missing for this build/account. */
    data object ProductsNotConfigured : BillingStatus
}

sealed interface PurchaseLaunch {
    data object Started : PurchaseLaunch
    data object NotReady : PurchaseLaunch
    data object UnknownProduct : PurchaseLaunch
    data class Failed(val code: Int) : PurchaseLaunch
}

interface BillingRepository {
    val status: StateFlow<BillingStatus>
    val products: StateFlow<List<ProProduct>>
    /** Latest answer from Google Play about what the user owns; null until the first query finishes. */
    val outcome: StateFlow<QueryOutcome?>
    /** True while a slow payment (e.g. cash, bank) is waiting for approval. */
    val purchasePending: StateFlow<Boolean>

    /** Connects (if needed) and re-reads products and purchases. Safe to call often. */
    suspend fun refresh()

    fun launchPurchase(activity: Activity, productId: String): PurchaseLaunch
}
