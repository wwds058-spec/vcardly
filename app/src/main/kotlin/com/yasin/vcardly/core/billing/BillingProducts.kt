package com.yasin.vcardly.core.billing

/**
 * Product IDs that MUST be created in Google Play Console (Monetize -> Products) before purchases can work.
 * Until they exist and the app is uploaded to a testing track, Play returns "no products" and the Pro screen says
 * purchases are not set up. See docs/PLAY_CONSOLE_SETUP.md.
 */
object BillingProducts {
    /** One-time ("in-app") product. */
    const val PRO_LIFETIME = "vcardly_pro_lifetime"
    /** Subscription; needs at least one base plan (e.g. yearly) and an offer to be purchasable. */
    const val PRO_YEARLY = "vcardly_pro_yearly"

    val proIds: Set<String> = setOf(PRO_LIFETIME, PRO_YEARLY)
}
