package com.yasin.vcardly.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.yasin.vcardly.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AdMob behind a consent gate. Nothing touches the network until Google's User Messaging Platform says ads may be requested
 * (GDPR / US state rules), and it is never started for Pro users or while the app is locked (the caller decides).
 *
 * Debug builds use Google's official TEST ad IDs. Release builds show ads ONLY when real AdMob IDs are supplied through
 * secrets.properties; otherwise [enabled] is false, so test ads can never ship. No contact data is ever passed to the ads SDK:
 * requests are plain `AdRequest.Builder().build()` with no keywords or content URLs.
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val enabled: Boolean = BuildConfig.ADS_ENABLED

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private var requested = false
    private var sdkStarted = false

    /** Gathers consent (showing Google's form when required) and then starts the ads SDK. Call from the foreground activity. */
    fun initialize(activity: Activity) {
        if (!enabled || requested) return
        requested = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { startIfAllowed(info) } },
            { startIfAllowed(info) }, // offline etc.: an earlier consent decision may still allow ads
        )
        // Consent gathered in a previous session lets us start without waiting for the network.
        startIfAllowed(info)
    }

    private fun startIfAllowed(info: ConsentInformation) {
        if (!info.canRequestAds() || sdkStarted) return
        sdkStarted = true
        MobileAds.initialize(context) { }
        _canRequestAds.value = true
    }

    /** True when the user is entitled to re-open their ad-consent choices (for example in the EEA). */
    fun privacyOptionsRequired(): Boolean =
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }
}
