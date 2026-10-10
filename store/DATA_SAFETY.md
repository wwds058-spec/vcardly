# VCardly — Play Console "Data safety" answers

Play Console → Policy → App content → Data safety. "Collected" in Google's sense means **sent off the device**. VCardly itself
sends nothing; the entries below come from Google libraries inside the app. Before submitting, compare with Google's own
guidance pages for each SDK, which can change:

- Google Mobile Ads (AdMob): developers.google.com/admob/android/privacy/play-data-disclosure
- ML Kit: developers.google.com/ml-kit/android-data-disclosure
- Google Play Billing: developer.android.com/google/play/billing (data safety section)

## First release (no ads) — use this now

The build CI produces has no AdMob IDs: the ads SDK never starts and the advertising-ID permission is removed (CI fails if
it comes back). Only ML Kit's anonymous diagnostics leave the device.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes |
| Do you provide a way for users to request that their data is deleted? | No (no account and no server data; everything is on the phone and is deleted by "Erase all data" or uninstalling) |

| Data type | Collected | Shared | Ephemeral? | Required or optional | Purposes |
|---|---|---|---|---|---|
| App info and performance → Diagnostics | Yes | No | No | Required (users cannot turn it off) | Analytics |

Everything else: **not collected** (see the list at the end). Advertising ID: **No**.

## Later, with ads (AdMob configured)

The answers below apply once AdMob is configured (free plan shows ads).

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes (through the ads and ML Kit libraries) |
| Is all of the user data collected by your app encrypted in transit? | Yes (the Google libraries use HTTPS) |
| Do you provide a way for users to request that their data is deleted? | No account or server data exists. Explain in the policy: everything is on the device and is deleted with "Erase all data" or by uninstalling; the advertising ID can be reset in Android settings |

## Data types

| Data type | Collected | Shared | Optional? | Purposes | Comes from |
|---|---|---|---|---|---|
| Device or other IDs (advertising ID) | Yes | Yes | Required for the free plan (Pro has no ads) | Advertising or marketing; Analytics; Fraud prevention, security and compliance | AdMob |
| Location → Approximate location (from IP address) | Yes | Yes | Required for the free plan | Advertising or marketing; Analytics; Fraud prevention, security and compliance | AdMob |
| App activity → App interactions (ad views and taps) | Yes | Yes | Required for the free plan | Advertising or marketing; Analytics | AdMob |
| App info and performance → Diagnostics | Yes | No | Required | Analytics | ML Kit (and AdMob) |
| App info and performance → Crash logs | Yes | Yes | Required for the free plan | Analytics | AdMob |
| Financial info → Purchase history | Check Google's Play Billing guidance; Google Play processes purchases, the app only receives a purchase token | | | App functionality | Play Billing |

**Not collected** (they never leave the phone unless the user shares or exports them): contacts' names, phone numbers, email
addresses and addresses; card photos; notes; follow-ups; the user's own card; the recognised card text; biometric data.

## Other declarations in App content

| Item | Answer |
|---|---|
| Ads | Contains ads: Yes (free plan) |
| Target audience | 18+ (business tool; not designed for children) |
| Content rating | Utility/productivity app: no violence, sexual content, gambling or user-generated content shared with others |
| Health / financial / government features | None |
| Exact alarm permission (`SCHEDULE_EXACT_ALARM`) | Used for user-set follow-up reminders at a chosen time ("alarm clock / reminder" use case). If Google asks you to drop it, reminders still work, a few minutes less precisely |
| Camera | Used only to photograph visiting cards while scanning |
