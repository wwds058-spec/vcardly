# VCardly — Play Store checklist

Step-by-step Play Console walkthrough with every answer: `store/PLAY_CONSOLE_STEPS.md`. Work through this before the first production release. Items marked **(you)** need your accounts or decisions; the code side is
already prepared. Details for billing, ads and declarations: `docs/PLAY_CONSOLE_SETUP.md`. Drive: `docs/GOOGLE_DRIVE_SETUP.md`.

## Build
- [ ] **(you)** Create an upload key and `keystore.properties` (see `keystore.properties.example`; never commit either).
- [ ] Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
- [ ] `./gradlew :app:bundleRelease` (R8 minify and resource shrinking are on).
- [ ] Install the release build on a real phone and test: scan a real card (front and back), OCR review and save, card images,
      reminders after a reboot, app lock, backup and restore (merge and replace, with and without a password), exports, QR sharing,
      dark mode, TalkBack on Home / Contacts / contact details, large font size.
- [ ] Confirm `app/schemas/` is committed; from the first release every schema change needs a Room migration (no destructive fallback).

## Monetisation (optional)
- [ ] **(you)** Play Console products `vcardly_pro_lifetime` (in-app) and `vcardly_pro_yearly` (subscription with a base plan).
- [ ] **(you)** Internal testing track + licence testers; test purchase, pending payment, refund, restore, offline after purchase.
- [ ] **(you)** AdMob app and banner unit, `secrets.properties` with `admob.appId` and `admob.bannerUnitId`, a published UMP
      consent message, `app-ads.txt` on your website. Without the IDs, release builds simply show no ads.

## Store listing
- [ ] **(you)** Publish `site/privacy.html` (fill in your name, support email and date) and use its URL as the privacy policy.
      With GitHub Pages: repository Settings → Pages → Source "Deploy from a branch" → this branch, folder `/ (root)`. The policy
      is then at `https://wwds058-spec.github.io/vcardly/site/privacy.html` (`.nojekyll` makes GitHub serve files as they are).
- [ ] **(you)** Data safety form: answers prepared in `store/DATA_SAFETY.md` (check them against Google's SDK pages linked there).
- [ ] Listing text prepared in `store/LISTING.md` (name, short and full description, release notes, screenshot captions).
- [ ] Ads declaration ("contains ads" if AdMob is configured).
- [ ] Permissions: CAMERA, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM (decide whether to keep it; inexact reminders work without it),
      RECEIVE_BOOT_COMPLETED, USE_BIOMETRIC, INTERNET, ACCESS_NETWORK_STATE, AD_ID (merged from the ads library).
- [ ] Content rating questionnaire; target audience: adults (not designed for children).
- [ ] Screenshots: the CI `ui-screenshots` branch has light and dark renders of every main screen with fictional data; take final
      ones on a real device for the listing.
- [ ] Short and full description, app icon (512 px, from the V mark), feature graphic.

## After release
- [ ] Watch Android vitals (crashes/ANRs), especially camera and OCR on low-end devices.
- [ ] Consider server-side purchase verification if Pro revenue matters.
