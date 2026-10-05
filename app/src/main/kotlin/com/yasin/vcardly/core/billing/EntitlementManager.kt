package com.yasin.vcardly.core.billing

import com.yasin.vcardly.domain.entitlement.EntitlementPolicy
import com.yasin.vcardly.domain.entitlement.EntitlementState
import com.yasin.vcardly.domain.entitlement.Feature
import com.yasin.vcardly.domain.entitlement.QueryOutcome
import com.yasin.vcardly.domain.entitlement.ScanAllowance
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.PreferencesRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The single place the rest of the app asks "may this user do X?". Nothing else may look at billing or ads directly.
 *
 *  - state starts from the cached answer (so a paying user never sees Free while Play connects)
 *  - every successful Play answer replaces the cache; failures keep it (offline-first, see [EntitlementPolicy.reconcile])
 *  - [isAllowed] and [scanAllowance] are the gates used by the UI
 */
@Singleton
class EntitlementManager @Inject constructor(
    private val billing: BillingRepository,
    private val prefs: PreferencesRepository,
    private val contacts: ContactRepository,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<EntitlementState> = combine(prefs.proCached, billing.outcome, billing.purchasePending) { cached, outcome, pending ->
        val pro = if (outcome == null) cached else EntitlementPolicy.reconcile(cached, outcome)
        EntitlementState(isPro = pro, purchasePending = pending)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, EntitlementState())

    init {
        // Persist definitive answers so the next launch starts correct even offline.
        scope.launch {
            billing.outcome.collect { outcome ->
                if (outcome is QueryOutcome.Success) prefs.setProCached(outcome.entitled)
            }
        }
        scope.launch { billing.refresh() }
    }

    fun isAllowed(feature: Feature): Boolean = EntitlementPolicy.isAllowed(feature, state.value)

    /** How many card scans this user may still start this month. Counts contacts created by scanning since the 1st. */
    suspend fun scanAllowance(): ScanAllowance {
        val since = EntitlementPolicy.monthStart(clock.instant(), clock.zone)
        val used = if (state.value.isPro) 0 else contacts.countScannedSince(since)
        return EntitlementPolicy.scanAllowance(state.value, used)
    }

    suspend fun refreshFromPlay() = billing.refresh()
}
