package com.yasin.vcardly.domain.entitlement

enum class PurchaseStateKind { PURCHASED, PENDING, UNSPECIFIED }

/** The parts of a Google Play purchase the app cares about. */
data class PurchaseSnapshot(
    val productIds: List<String>,
    val state: PurchaseStateKind,
    val acknowledged: Boolean,
    val token: String,
)

data class Interpretation(
    val entitled: Boolean,
    val pending: Boolean,
    /** Purchases that must be acknowledged; Google refunds unacknowledged purchases after 3 days. */
    val tokensToAcknowledge: List<String>,
)

/** Pure decision logic over what Google Play reports. */
object PurchaseInterpreter {
    fun interpret(purchases: List<PurchaseSnapshot>, proProductIds: Set<String>): Interpretation {
        val pro = purchases.filter { p -> p.productIds.any { it in proProductIds } }
        val completed = pro.filter { it.state == PurchaseStateKind.PURCHASED }
        return Interpretation(
            entitled = completed.isNotEmpty(),
            // A pending purchase (slow payment method) must NOT unlock anything yet.
            pending = pro.any { it.state == PurchaseStateKind.PENDING },
            tokensToAcknowledge = completed.filter { !it.acknowledged }.map { it.token },
        )
    }
}
