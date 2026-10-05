package com.yasin.vcardly.presentation.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.R
import com.yasin.vcardly.core.billing.EntitlementManager
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.domain.entitlement.ScanAllowance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Checks the free plan's monthly scan allowance before the scanner opens. */
@HiltViewModel
class ScanGateViewModel @Inject constructor(private val entitlements: EntitlementManager) : ViewModel() {
    fun request(onAllowed: () -> Unit, onBlocked: (limit: Int) -> Unit) {
        viewModelScope.launch {
            when (val a = entitlements.scanAllowance()) {
                ScanAllowance.Unlimited -> onAllowed()
                is ScanAllowance.Limited -> if (a.isAllowed) onAllowed() else onBlocked(a.limit)
            }
        }
    }
}

/**
 * Returns a "start scanning" action shared by every entry point (the centre Scan button, Home, Contacts). When the
 * free allowance is used up it shows an honest explanation with a route to Pro instead of opening the camera.
 */
@Composable
fun rememberScanLauncher(onAllowed: () -> Unit, onUpgrade: () -> Unit): () -> Unit {
    val gate: ScanGateViewModel = hiltViewModel()
    var limit by remember { mutableStateOf<Int?>(null) }
    val allowed by rememberUpdatedState(onAllowed)
    limit?.let { l ->
        ConfirmDialog(
            title = stringResource(R.string.scan_limit_title),
            message = stringResource(R.string.scan_limit_message, l),
            confirmText = stringResource(R.string.pro_see_pro),
            onConfirm = { limit = null; onUpgrade() },
            onDismiss = { limit = null },
        )
    }
    return remember(gate) { { gate.request(onAllowed = { allowed() }, onBlocked = { limit = it }) } }
}
