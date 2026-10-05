package com.yasin.vcardly.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.yasin.vcardly.core.common.AppLog
import com.yasin.vcardly.domain.entitlement.PurchaseInterpreter
import com.yasin.vcardly.domain.entitlement.PurchaseSnapshot
import com.yasin.vcardly.domain.entitlement.PurchaseStateKind
import com.yasin.vcardly.domain.entitlement.QueryOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Google Play Billing, for real. It cannot be exercised without Play Console products, a signed upload on a testing track and a
 * license-tester account (see docs/PLAY_CONSOLE_SETUP.md); until then it simply reports [BillingStatus.ProductsNotConfigured].
 * Client-side only: there is no backend, so purchases are not verified server-side (a rooted/modified device could fake Pro).
 * If revenue matters, verify tokens with the Google Play Developer API from a server before trusting them.
 */
@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : BillingRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _status = MutableStateFlow<BillingStatus>(BillingStatus.Connecting)
    private val _products = MutableStateFlow<List<ProProduct>>(emptyList())
    private val _outcome = MutableStateFlow<QueryOutcome?>(null)
    private val _pending = MutableStateFlow(false)
    override val status: StateFlow<BillingStatus> = _status.asStateFlow()
    override val products: StateFlow<List<ProProduct>> = _products.asStateFlow()
    override val outcome: StateFlow<QueryOutcome?> = _outcome.asStateFlow()
    override val purchasePending: StateFlow<Boolean> = _pending.asStateFlow()

    private val details = mutableMapOf<String, ProductDetails>()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        // A purchase just finished (or failed). Re-reading from Play is simpler and safer than trusting the callback payload.
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) scope.launch { refresh() }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override suspend fun refresh() = mutex.withLock {
        if (!connect()) {
            _status.value = BillingStatus.Unavailable
            _outcome.value = QueryOutcome.Failed
            return@withLock
        }
        loadProducts()
        loadPurchases()
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        _status.value = BillingStatus.Connecting
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                // The next refresh() reconnects; nothing to do here.
                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    private suspend fun loadProducts() {
        suspend fun query(type: String, id: String): ProductDetails? {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()))
                .build()
            return client.queryProductDetails(params).productDetailsList?.firstOrNull()
        }
        val lifetime = query(BillingClient.ProductType.INAPP, BillingProducts.PRO_LIFETIME)
        val yearly = query(BillingClient.ProductType.SUBS, BillingProducts.PRO_YEARLY)
        details.clear()
        listOfNotNull(lifetime, yearly).forEach { details[it.productId] = it }

        _products.value = listOfNotNull(
            lifetime?.oneTimePurchaseOfferDetails?.let { ProProduct(lifetime.productId, ProductKind.LIFETIME, it.formattedPrice) },
            yearly?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()
                ?.let { ProProduct(yearly.productId, ProductKind.YEARLY, it.formattedPrice) },
        )
        _status.value = if (_products.value.isEmpty()) BillingStatus.ProductsNotConfigured else BillingStatus.Ready
    }

    private suspend fun loadPurchases() {
        val purchases = mutableListOf<Purchase>()
        for (type in listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)) {
            val r = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
            if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _outcome.value = QueryOutcome.Failed // keep the cached answer rather than guessing
                return
            }
            purchases += r.purchasesList
        }
        val snapshots = purchases.map {
            PurchaseSnapshot(
                productIds = it.products,
                state = when (it.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> PurchaseStateKind.PURCHASED
                    Purchase.PurchaseState.PENDING -> PurchaseStateKind.PENDING
                    else -> PurchaseStateKind.UNSPECIFIED
                },
                acknowledged = it.isAcknowledged,
                token = it.purchaseToken,
            )
        }
        val result = PurchaseInterpreter.interpret(snapshots, BillingProducts.proIds)
        // Unacknowledged purchases are refunded by Google after 3 days, so acknowledge as soon as we see them.
        result.tokensToAcknowledge.forEach { token ->
            val ack = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
            AppLog.d(TAG, "acknowledge code=${ack.responseCode}")
        }
        _pending.value = result.pending
        _outcome.value = QueryOutcome.Success(result.entitled)
    }

    override fun launchPurchase(activity: Activity, productId: String): PurchaseLaunch {
        if (!client.isReady) return PurchaseLaunch.NotReady
        val d = details[productId] ?: return PurchaseLaunch.UnknownProduct
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).apply {
            // Subscriptions must say which offer is bought; the first offer is the plain base plan.
            d.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { setOfferToken(it) }
        }.build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) PurchaseLaunch.Started else PurchaseLaunch.Failed(result.responseCode)
    }

    private companion object {
        const val TAG = "Billing"
    }
}
