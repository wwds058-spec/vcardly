package com.yasin.vcardly.presentation.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.billing.BillingRepository
import com.yasin.vcardly.core.billing.BillingStatus
import com.yasin.vcardly.core.billing.EntitlementManager
import com.yasin.vcardly.core.billing.ProProduct
import com.yasin.vcardly.core.billing.PurchaseLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProUiState(
    val isPro: Boolean = false,
    val purchasePending: Boolean = false,
    val status: BillingStatus = BillingStatus.Connecting,
    val products: List<ProProduct> = emptyList(),
    val restoring: Boolean = false,
    val launchFailed: Boolean = false,
)

@HiltViewModel
class ProViewModel @Inject constructor(
    private val entitlements: EntitlementManager,
    private val billing: BillingRepository,
) : ViewModel() {
    private val restoring = MutableStateFlow(false)
    private val launchFailed = MutableStateFlow(false)

    val uiState: StateFlow<ProUiState> = combine(
        entitlements.state, billing.status, billing.products, restoring, launchFailed,
    ) { e, status, products, r, failed -> ProUiState(e.isPro, e.purchasePending, status, products, r, failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProUiState())

    init { restore() }

    fun purchase(activity: Activity, productId: String) {
        launchFailed.value = billing.launchPurchase(activity, productId).let { it != PurchaseLaunch.Started }
    }

    /** Re-reads what Google Play says the user owns (also how "restore purchases" works on a new phone). */
    fun restore() {
        restoring.value = true
        viewModelScope.launch {
            entitlements.refreshFromPlay()
            restoring.value = false
        }
    }
}
