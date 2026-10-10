# VCardly — publishing on Google Play, step by step

Everything below is for the **first release without ads** (no AdMob account connected yet), which is what CI builds today:
no ad SDK starts, the advertising-ID permission is removed, and the Pro screen honestly says purchases are not set up. Ads
and Pro can be switched on in a later update (see the end).

Exact texts referenced here: `store/LISTING.md` (listing), `store/DATA_SAFETY.md` (Data safety), `site/privacy.html`
(privacy policy), images in `store/graphics/`.

---

## 0. Before you start (one time)

1. **Upload key into GitHub** (only you can do this; values are in the private file you received in the chat):
   repository → Settings → Secrets and variables → Actions → New repository secret, four times:
   `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`.
   Then re-run CI (Actions → CI → latest run → "Re-run all jobs"). When it is green, the signed bundle is at
   `https://github.com/wwds058-spec/vcardly/raw/release-aab/vcardly-release.aab` and `mapping.txt` next to it.
2. **Privacy policy online**: in `site/privacy.html` replace `[YOUR NAME OR BUSINESS NAME]`, `[YOUR SUPPORT EMAIL]`
   and `[DATE YOU PUBLISH]`; then repository → Settings → Pages → Source "Deploy from a branch" → branch
   `claude/happy-goodall-tjw9vc`, folder `/ (root)` → Save. After a minute it is at
   `https://wwds058-spec.github.io/vcardly/site/privacy.html`. Open it once to check.
3. A **support email** that will be shown publicly on the listing.

## 1. Create the app

Play Console → **Create app**:

| Field | Answer |
|---|---|
| App name | VCardly: Business Card Scanner |
| Default language | English (United States) — or English (United Kingdom); the app text is English |
| App or game | App |
| Free or paid | Free |
| Declarations | tick Developer Program Policies and US export laws |

## 2. Dashboard → "Set up your app" (App content)

| Section | Answer |
|---|---|
| **Privacy policy** | `https://wwds058-spec.github.io/vcardly/site/privacy.html` |
| **App access** | All functionality is available without special access (there is no login) |
| **Ads** | **No, my app does not contain ads** (true for this build; change it in the update that adds AdMob) |
| **Content rating** | Start questionnaire → email → category **"All other app types"** (utility/productivity). Answer **No** to violence, sexuality, language, controlled substances, gambling, user-generated content shared between users, and location sharing. Expected result: rated for everyone (IARC 3+/E). |
| **Target audience** | **18 and over** only. "Appeals to children": No. |
| **News app** | No |
| **COVID-19 contact tracing** | No |
| **Data safety** | Use the "First release (no ads)" table in `store/DATA_SAFETY.md` |
| **Government app** | No |
| **Financial features** | My app doesn't provide any financial features |
| **Health** | My app does not have any health features |
| **Advertising ID** | **No** (this build does not declare the permission; CI checks it) |

### If Play Console asks about permissions

The release build declares: CAMERA, INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC, USE_FINGERPRINT, POST_NOTIFICATIONS,
SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, BILLING, WAKE_LOCK, FOREGROUND_SERVICE (CI prints this list on every build).

- **Foreground service**: only the generic permission from Android's WorkManager library; VCardly declares no
  foreground-service types, so there is nothing to declare. If asked anyway: the app does not use foreground services.
- **Exact alarms** (`SCHEDULE_EXACT_ALARM`): follow-up reminders at a time the user sets. Android 14+ asks the user to
  allow it; reminders still arrive (a few minutes less precisely) if they do not.
- **No advertising ID** and none of Android's ad-services permissions in this build (CI fails if one appears).

## 3. Store listing

Grow → Store presence → **Main store listing** (copy from `store/LISTING.md`):

- App name, short description, full description: from `store/LISTING.md`.
- App icon: `store/graphics/icon-512.png`
- Feature graphic: `store/graphics/feature-graphic.png`
- Phone screenshots: the six files in `store/graphics/screenshots/` (in their numbered order).
- Tablet screenshots: optional; skip for now.

Store settings: Category **Business**, contact email, (website optional: the GitHub Pages address is fine).

## 4. Closed test (required for new personal accounts)

Personal developer accounts created after 13 November 2023 must run a **closed test with at least 12 testers who stay
opted in for 14 days in a row** before they can apply for production. Check Dashboard: if it shows this requirement, do this:

1. Testing → **Closed testing** → Create track (or use "Alpha") → **Testers**: create an email list with at least 12
   Google-account emails (friends, colleagues), save, and copy the **opt-in link**.
2. **Create new release** → Play App Signing: accept "Use Google-generated key" (recommended) → upload
   `vcardly-release.aab` → release name `1.0.0 (versionCode N)` (filled in automatically) → release notes from
   `store/LISTING.md` ("What's new") → Save → Review release → **Start rollout to Closed testing**.
3. App bundle explorer → the new version → Downloads/Assets → **Deobfuscation file**: upload `mapping.txt` from the same
   `release-aab` branch, so crash reports show real code lines.
4. Send testers the opt-in link. They tap "Become a tester", then install from the Play Store link. They must keep it
   installed and opted in for 14 days; ask them to open it a few times and use the feedback option.
5. After 14 days: Dashboard → **Apply for production**, answer the short questionnaire about the test, wait for approval
   (usually a few days), then Production → Create new release → add the latest `.aab` → rollout (start with 20%).

If your account does not show the requirement, you can go straight to Production (step 5) after an Internal test.

## 5. Updates later

Every push to the branch makes CI build a new `.aab` with a higher versionCode automatically. For an update: download
the latest `vcardly-release.aab` and `mapping.txt`, create a new release on the track, upload both. Change `versionName`
in `app/build.gradle.kts` for user-visible version numbers.

## 6. Turning on ads and Pro later (optional)

- **Ads**: create an AdMob app and banner unit, put `admob.appId` and `admob.bannerUnitId` in `secrets.properties`
  (and as CI secrets if CI should build it), publish a consent message in AdMob, add `app-ads.txt` to your website. The
  build then includes the ads SDK again with the advertising ID. In Console change **Ads → Yes**, **Advertising ID →
  Yes (Advertising, Analytics, Fraud prevention)** and use the ads table in `store/DATA_SAFETY.md`.
- **Pro purchases**: create `vcardly_pro_lifetime` (one-time product) and `vcardly_pro_yearly` (subscription) in
  Monetise → Products, then test with licence testers (`docs/PLAY_CONSOLE_SETUP.md`).
