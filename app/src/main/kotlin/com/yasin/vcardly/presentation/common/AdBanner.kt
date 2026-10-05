package com.yasin.vcardly.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.yasin.vcardly.BuildConfig
import com.yasin.vcardly.R
import com.yasin.vcardly.core.ads.AdsManager
import com.yasin.vcardly.core.billing.EntitlementManager
import com.yasin.vcardly.domain.entitlement.AdPlacement
import com.yasin.vcardly.domain.entitlement.EntitlementPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** True while the app lock cover is showing; ads must not load or count impressions on a screen nobody can see. */
val LocalAppLocked = staticCompositionLocalOf { false }

@HiltViewModel
class AdBannerViewModel @Inject constructor(entitlements: EntitlementManager, ads: AdsManager) : ViewModel() {
    val eligible: StateFlow<Boolean> = combine(entitlements.state, ads.canRequestAds) { state, can ->
        EntitlementPolicy.shouldShowAds(state, adsEnabled = ads.enabled, canRequestAds = can, appLocked = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

/**
 * Adaptive banner for the free plan. Renders nothing for Pro users, before consent, when ads are not configured, while locked,
 * or after a failed load (no empty gap). [placement] documents WHY this spot is allowed; see [AdPlacement].
 */
@Composable
fun AdBanner(@Suppress("UNUSED_PARAMETER") placement: AdPlacement, modifier: Modifier = Modifier, viewModel: AdBannerViewModel = hiltViewModel()) {
    val eligible by viewModel.eligible.collectAsStateWithLifecycle()
    if (!eligible || LocalAppLocked.current) return

    var failed by remember { mutableStateOf(false) }
    if (failed) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var adView by remember { mutableStateOf<AdView?>(null) }

    DisposableEffect(lifecycle, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView?.pause()
                Lifecycle.Event.ON_RESUME -> adView?.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(modifier.fillMaxWidth()) {
        // Ads are labelled so they can never be mistaken for content.
        Text(stringResource(R.string.ad_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value.toInt()
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                        adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) { failed = true }
                        }
                        loadAd(AdRequest.Builder().build())
                        adView = this
                    }
                },
                onRelease = { it.destroy(); adView = null },
            )
        }
    }
}
