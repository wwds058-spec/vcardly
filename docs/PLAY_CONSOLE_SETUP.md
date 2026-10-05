# Play Billing and AdMob: what is built and what you must configure

**Nothing here is faked.** Billing talks to the real Google Play Billing Library and AdMob to the real Google Mobile Ads SDK.
Both need accounts and IDs that only you can create, so until you do, purchases report "not set up" and release builds ship with
ads OFF. Debug builds use Google's official **test** ad IDs.

## Free vs Pro (one file: `domain/entitlement/EntitlementPolicy.kt`)
| | Free | Pro |
|---|---|---|
| Contacts, tags, categories, follow-ups, reminders | yes | yes |
| Backup / restore (encrypted too), vCard + CSV export, app lock, erase-all | yes | yes |
| Card scans | 25 per month (contacts created by scanning since the 1st) | unlimited |
| PDF report, Excel export | no | yes |
| Ads (one banner on Home) | yes | none |

Own-data portability and security are deliberately not paywalled. Change the split by editing `Feature`, `EntitlementPolicy` and the
strings; every gate asks `EntitlementManager`.

## Google Play Console (billing) - you must do this
1. Create the app in Play Console with package `com.yasin.vcardly` and upload a signed build to **Internal testing** (billing only
   works for apps installed from Play, signed with the upload/app-signing key).
2. Monetize -> Products:
   - **In-app product** with ID exactly `vcardly_pro_lifetime` (one-time, activate it).
   - **Subscription** with ID exactly `vcardly_pro_yearly`, with at least one base plan (yearly) and offer, activated.
   (IDs live in `core/billing/BillingProducts.kt`; change both places if you pick other names.)
3. Setup -> License testing: add tester accounts. Test: buy, cancel/refund, a slow ("pending") payment, reinstall + Restore purchases,
   airplane mode after a purchase (must stay Pro).
4. The Pro screen shows prices straight from Google Play (localized); never hard-code prices.
5. **Server-side verification is NOT implemented** (the app has no backend). A modified/rooted device could fake Pro. If revenue
   matters, send purchase tokens to your server and verify with the Google Play Developer API (and use Real-time developer
   notifications for refunds/cancellations) before trusting them.

## AdMob (ads) - you must do this
1. Create an AdMob account, add the Android app, and create one **banner** ad unit.
2. Copy `secrets.properties.example` to `secrets.properties` (git-ignored) and fill `admob.appId` and `admob.bannerUnitId`.
   Without both, **release builds show no ads**. Debug builds always use Google's test IDs (never click your own live ads).
3. AdMob -> Privacy & messaging: create and publish a **GDPR message** (and US state regulations message if you target them). The app
   uses Google's User Messaging Platform; ads are not requested until consent allows it. Without a published message no form is shown.
4. Host `app-ads.txt` on your developer website and set the website in the Play listing (needed for full demand).
5. Placements: Home only (`AdPlacement`). Never add one on a screen that shows an individual's data; a unit test guards the list.

## Play Console declarations that must match the app
- **Data safety**: the app collects/shares **Device or other IDs (advertising ID)** and, via the ads SDK, **approximate location, app
  interactions, diagnostics** for advertising on the free plan. Contacts and card images are **not** collected or shared (they never
  leave the device). Purchases are handled by Google Play. Re-check against the SDK versions you ship.
- **Ads declaration**: "Contains ads: yes". **Permissions**: INTERNET + ACCESS_NETWORK_STATE (ads/billing), AD_ID (added by the ads
  library; declare in the advertising ID question), SCHEDULE_EXACT_ALARM (see PROJECT_STATUS.md), CAMERA, POST_NOTIFICATIONS,
  USE_BIOMETRIC, RECEIVE_BOOT_COMPLETED.
- **Target audience**: not directed at children; do not enable "designed for families".
- The in-app **Privacy** screen already describes ads and purchases; add a public privacy-policy URL to the Play listing that says the
  same, and keep the two in sync.
