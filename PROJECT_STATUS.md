# VCardly — Project Status

Offline-first Android visiting-card book. Package `com.yasin.vcardly`, single `:app` module, Kotlin + Jetpack Compose +
Material 3, MVVM, Hilt, Room, DataStore. Architecture: `docs/ARCHITECTURE.md` (the technical source of truth).

> **For the next session:** read this file top to bottom, then `docs/ARCHITECTURE.md`. Builds cannot run inside the Claude
> sandbox (Google Maven is blocked); every push is built and tested by GitHub Actions (`.github/workflows/ci.yml`), and the
> emulator job publishes screenshots of the redesigned screens to the `ui-screenshots` branch
> (`git fetch origin ui-screenshots`). Use those screenshots to review UI changes.

## Last build / last test

| Check (GitHub Actions, clean Ubuntu runner, JDK 17) | Last result |
|---|---|
| `:app:assembleDebug` | passes (run 37344619895, commit `c384342`) |
| `:app:testDebugUnitTest` (JVM unit tests) | passes (same run) |
| `:app:lintDebug` | passes, 0 errors (same run) |
| `:app:connectedDebugAndroidTest` on an API 30 emulator, Pixel 6 profile: Room/repository tests, `ScreenBehaviourTest` (12 UI interaction tests), `ScreenshotTest` (27 screens, light and dark) | passes (same run) |
| iOS (`ios` job, macos-15, Xcode 16.4, iPhone 16 Pro simulator): XcodeGen, build, unit + repository + screenshot tests (26 screens) | passes, 53 unit/screenshot tests + accessibility audit UI test (no blocking issues); iPhone-written backups verified by `tools/backup_reference.py`, exports opened with `csv`/`openpyxl` (run 37406964193) |
| Debug APKs | `debug-apk` branch (universal) and `debug-apk-arm64` branch (arm64 only), rebuilt on every push |

Run 37410962605 (commit `8376bf5`) passed every job: build, unit tests (including `CardEdgeDetectorTest`), lint, the instrumented
tests with Accessibility Test Framework checks on every screenshot screen (no errors) and the 2x-font screenshots, iOS and both APKs.

**Never verified (needs a person with a phone):** the real camera and OCR on real cards, reminders after a reboot, the biometric
prompt, Play Billing and AdMob (need your accounts), the PDF layout, TalkBack on a device, the launcher icon on different launchers.

## Completed

- **Original feature build (phases 1–11)**: everything in "History" below — Room schema, contacts, categories and tags, favourites,
  card images, CameraX + ML Kit OCR with an always-editable review, follow-ups and reminders that survive reboot and time changes,
  digital card, QR and vCard, reports with PDF/CSV/Excel export, global search, `vcardly-backup-v1` backup/restore (optionally
  encrypted), app lock, Free/Pro entitlements with Play Billing, AdMob with test IDs, privacy screen, CI.
- **2026-10 premium redesign** (this session), following the reference image and `docs/ARCHITECTURE.md`:
  - **Design system** (`core/designsystem`): bundled Plus Jakarta Sans (OFL licence in `assets/licenses`), brand palette with a
    light theme and a deep-navy dark theme (not an inversion), tone families (blue, rose, mint, orange, lavender, navy), spacing and
    elevation tokens, and the component set: `VCardlyTopBar`, `VCardlyScreenHeader`, `VCardlySearchBar`/`VCardlySearchLauncher`,
    `VCardlyStatCard`, `VCardlyActionCard` (gradient tiles), `VCardlySectionHeader`, `VCardlyChip`, primary/secondary/tonal/text/icon
    buttons, `VCardlyFab`, `VCardlyEmptyState` (illustrated), `VCardlyLoadingState` (skeletons, or a message such as "Reading your
    card…"), `VCardlyErrorState`, `VCardlyNotice`, `VCardlyAvatar`, `VCardlyInfoRow`, `VCardlyTimeline`, `VCardlyStepper`,
    `VCardlyNavigationRow`, `VCardlyBottomNavigation` (four tabs plus a raised centre Scan action), `VCardlyLogo`.
    Shared domain-aware UI in `presentation/common`: `VCardlyContactCard`, `VCardlyContactMini`, `VCardlyFollowUpCard`,
    `VCardlyDigitalCard`, `BusinessCardArt`, `CardImageView` + full-screen zoom viewer, `ExternalActions` (call, WhatsApp, email,
    maps, copy).
  - **Screens redesigned**: onboarding, Home (greeting with the user's own name from My card, tinted stats, quick actions, upcoming
    follow-ups, recent contacts, network breakdown, all from Room), Contacts (header with count, chips, cards with Call/WhatsApp/Email
    shortcuts, tag filter sheet), contact details (hero card image or generated card, quick actions, Details/Card images/Notes/
    Follow-ups tabs, copyable info rows, activity timeline derived from stored timestamps), add/edit (5-step form with card images from
    camera or gallery, rotate, replace, remove), OCR review ("Scanned successfully, we found N details", Rescan / Save Contact, honest
    "Couldn't read this card" with Try again / Enter manually), full-screen scanner (card frame, corner brackets, flash Auto/On/Off,
    gallery, scan-line animation) and crop, Follow-ups (tabs with counts, day groups, animated complete) and the follow-up editor
    (type chips, quick scheduling, reminder card, Mark done / Cancel / Reopen / Delete), My digital card + QR (field chips, Share, Save
    QR as PNG through the file picker), Reports (stats, category donut, growth bars, follow-up health, export tiles), Settings (grouped
    icon rows with bottom sheets), Backup (status, protected backup, staged restore, honest Drive card), Pro, Lock, Search, Privacy.
  - **Navigation**: same four top-level destinations; Scan is a centre action gated by the free-plan scan quota
    (`rememberScanLauncher`), not a fifth destination. Short fade/rise transitions.
  - **Domain additions** (no schema change): follow-up types WhatsApp, Quotation, Payment; statuses Rescheduled (active) and Cancelled
    (closed); moving an active follow-up's time marks it Rescheduled; system categories Business, Customer, Supplier (appended for
    existing installs on database open); `observeRecent` query for Home.
  - **Tests**: `ScreenshotTest` and `ScreenBehaviourTest` (androidTest, stateless screen content with fictional data),
    `ThemeContrastTest` and `WhatsAppDigitsTest` (JVM).
  - New launcher/splash icon (V mark on a navy-to-blue gradient, themed monochrome variant).

- **iOS app (2026-10)**: native SwiftUI iPhone app in `ios/` with the same design system and screens (onboarding, Home,
  Contacts, contact details, 4-step add/edit, scan with VisionKit + Vision OCR and review, Follow-ups + editor, My card + QR/vCard
  share, Reports with Swift Charts, Settings, Search, Privacy, Pro (honest "not set up"), Lock). SwiftData, local notifications,
  Face ID lock. Unit, repository and screenshot tests on a macOS CI runner; screenshots on the `ios-screenshots` branch.
  Details, gaps and the Apple-account steps: `docs/IOS.md`.
- **iOS backup and restore**: the Android `vcardly-backup-v1` format (optional password encryption, staged and checksum-verified
  restore, Merge or Replace), so backups move between iPhone and Android. Cross-platform proof: shared fixtures in
  `testdata/backup/` restored by both test suites, plus CI verification of iPhone-written files with `tools/backup_reference.py`.
- **iOS import and export**: vCard import (file, or iPhone Contacts via the system picker, no permission) with the same review
  list as Android, and export of all contacts to .vcf. Fixed on both platforms: text cut off from an overlong job title,
  company or address on import is now kept in the notes (it was silently dropped).
- **iOS report exports**: PDF, CSV and Excel from Reports (ports of Android's writers), checked in CI with `csv`/`openpyxl`.
  PDF and Excel are free on iPhone until purchases exist (owner's decision, `ProFeatures.exportsRequirePro`).
- **iOS accessibility pass**: Apple accessibility audit UI test on seven screens (blocking on contrast, hit regions,
  labels and traits) and AX3 large-text screenshots; fixes listed in `docs/IOS.md` ("Accessibility checks").
- **Android Organize and Transfer redesign**: categories with their colour, contact counts and built-in marker; tags; a clearer
  import preview and export card. Same stateless `XxxContent` pattern, with screenshots and behaviour tests.
- **Android accessibility checks**: every screenshot screen is also checked with Google's Accessibility Test Framework (the
  engine of Accessibility Scanner: touch targets, contrast measured on the real screenshot, labels, duplicate descriptions).
  Errors fail CI; warnings are listed in the CI log. Eight main screens are also rendered at 2x font (`30_large_*.png`).
  Fixed: text fields hid the typed text from TalkBack (the label was a content description; it is now Material's label
  slot); at very large fonts, top-bar titles were squeezed by their actions, the form stepper labels were cut to a few
  letters, stat and list cards cut their text, and the category donut legend overlapped its percentages
  (`core/designsystem/theme/TextScale.kt`, from 1.5x).
- **Android card-edge detection in the scanner**: after a capture (or a picked photo, and after each rotation) the crop starts
  on the card instead of a fixed inset. `domain/scan/CardEdgeDetector` is plain Kotlin on a 320 px grayscale copy: Sobel
  edges, straight-line candidates up to about 7 degrees of tilt, and the best four lines that trace a card-shaped rectangle
  along at least 60% of every side. No clear card means the old default crop; the user can always drag the corners or pick
  "Whole image" or "Card edges". Tested on synthetic photos (contrast, tilt, uneven light, a table edge, text without a
  card); **not yet tried on real card photos**.
- **Android camera fixes (reported by the owner: "camera not working")**: the capture screen released the camera right
  after binding it (its cleanup read the provider state at dispose time), and the binding could run off the main thread
  after `await()` ("Not in application's main thread", shown as "The camera could not be started"). Both fixed; a failed
  photo now shows a message. `CameraCaptureTest` opens the real screen on the CI emulator (now started with
  `-camera-back emulated`) and checks the camera stays open; it failed before each fix and passes after (run 37429798155).
- **Android perspective correction**: the crop is four free corners (`CropQuad`, `CropMath`: convex, no side under 10%,
  tested). A card photographed at an angle is straightened into a rectangle with `Matrix.setPolyToPoly` at full
  resolution, sized by the longer of each pair of opposite sides; an upright rectangle stays a plain crop. Screenshot
  `18_scan_crop_quad`.

## In progress

- Nothing half-done is committed. Next: trying edge detection and straightening on real card photos (a device is needed).
  iPhone work is paused by the owner until later.

## Blocked

- Nothing blocked inside the code. Items that need you are under "External configuration required".

## External configuration required

| What | Where it is prepared | What you need to do |
|---|---|---|
| Release signing | `app/build.gradle.kts` reads `keystore.properties` (see `keystore.properties.example`) | create an upload key; never commit it |
| AdMob | debug builds use Google's test IDs; release ads stay off until `secrets.properties` has `admob.appId` and `admob.bannerUnitId` | create the app and banner unit, publish a UMP consent message |
| Play Billing | `PlayBillingRepository`, products `vcardly_pro_lifetime` (in-app) and `vcardly_pro_yearly` (subscription) | create the products, upload to a testing track, add licence testers (`docs/PLAY_CONSOLE_SETUP.md`); the Pro screen honestly says "not set up" until then |
| Google Drive backup | `CloudBackupProvider` with `UnconfiguredDriveProvider` | Cloud project, OAuth client, Drive appdata scope (`docs/GOOGLE_DRIVE_SETUP.md`) |
| iOS signing, App Store, in-app purchases | `ios/project.yml` (empty team), Pro screen says not set up | Apple Developer account; see `docs/IOS.md` |
| Store listing | `PLAY_STORE_CHECKLIST.md` | privacy-policy URL, Data-safety form, screenshots, content rating |

## Known limitations

- No server: purchases are verified client-side only; a modified device could fake Pro (documented in the billing code).
- WhatsApp links need the number in international format; numbers without a country code cannot be opened in WhatsApp (the action is
  hidden when the number is too short to be valid).
- The contact activity timeline only shows events that have timestamps (created, updated, follow-ups planned/completed/cancelled);
  there is no separate history table, so individual note edits are not listed.
- Profile photo for My card is not supported yet (initials are used).
- Older app versions that read a backup containing Rescheduled/Cancelled follow-ups treat them as Pending.
- Localization: English only by decision; all text is in `strings.xml`, layouts are RTL-safe, and Plus Jakarta Sans falls back to the
  system font for Telugu, Devanagari and Urdu glyphs.
- Remaining lint warnings are informational (newer library versions, plurals candidates, deprecated non-mirrored icons).

## History (original build phases)

## Phase 11 contents

- **Translations: dropped by your decision.** The app is English only; every string is still in `strings.xml` (none hard-coded), the manifest
  has `supportsRtl`, and layouts use start/end, so adding a language later is a translation job, not a refactor.
- **CI** (`.github/workflows/ci.yml`): build, unit tests, lint, and an emulator job for the instrumented tests; uploads the debug APK and reports.
- **Accessibility fixes**: avatar initials now use black or white ink chosen per background colour (white failed WCAG on orange and yellow
  category colours; unit-tested for every built-in and palette colour) and are hidden from TalkBack; "Open contact"/"Edit follow-up" click
  labels on list rows; earlier phases already gave every icon button a description, 48dp touch targets, merged semantics on stat cards and chart
  bars, error text exposed to screen readers, and a non-colour cue for locked Pro features.
- **Hardening**: `usesCleartextTraffic=false`; `allowBackup=false` + data-extraction rules; no exported components except the launcher activity;
  R8 keep rules for the backup serializers; AndroidX `ExifInterface` instead of the framework class; audit found no hard-coded UI strings and
  no PII in logs (`AppLog` only gets counts/ids and only runs in debug).
- **Still to do before a Play release (needs you)**: signing key, AdMob IDs, Play products, privacy-policy URL and Data-safety form
  (`docs/PLAY_CONSOLE_SETUP.md`), a pass on a real device (see "Never verified" above), and bumping library versions.

## Phase 10 contents

- **`EntitlementManager`** (`core/billing`) is the only thing the app asks "may this user do X?". Pure rules live in
  `domain/entitlement/EntitlementPolicy` (tested): `Feature` = PDF report, Excel export, unlimited scans, ad-free. **Free keeps
  everything about your own data and safety** (contacts, reminders, backup/restore incl. encrypted, vCard + CSV export, app lock,
  erase-all); Free gets 25 card scans per calendar month (counted from contacts created by scanning since the 1st; deleting
  contacts lowers the count, accepted as a soft limit). Change the offer in that one file.
- **Play Billing** (`PlayBillingRepository`, real `billing-ktx` 7.x): connects, queries two products (`vcardly_pro_lifetime` in-app,
  `vcardly_pro_yearly` subscription), reads purchases, **acknowledges** them (Google refunds unacknowledged purchases after 3 days),
  launches the purchase flow, handles **pending** payments (never unlocks until PURCHASED; tested), restores purchases by re-querying.
  Prices come from Google Play (localized), never hard-coded. Offline-first: a successful Play answer replaces the cache (refunds and
  expiry take effect); if Play cannot be reached the last answer is kept, so a paying user is not downgraded while offline (tested).
  **Needs your Play Console setup** (products, testing track, license testers): until then the Pro screen honestly says purchases are
  not set up. **Not server-verified** (no backend): a modified device could fake Pro; see `docs/PLAY_CONSOLE_SETUP.md`.
- **Gates**: Reports PDF/Excel buttons show "· Pro" and route free users to the Pro screen (and the ViewModel refuses as a second
  line of defence); starting a scan checks the monthly quota and offers the upgrade; Settings shows Upgrade / Pro active.
- **AdMob** (`AdsManager`, `AdBanner`): Google's official **test** App/banner IDs in every debug build; **release builds show ads only if
  `secrets.properties` supplies real IDs** (otherwise OFF, so test ads can never ship). Consent first: Google's User Messaging
  Platform form, ads SDK not started until `canRequestAds`; "Ad privacy choices" in Settings when required. Never started for Pro users or
  while the app is locked (a hidden screen must not log impressions). One adaptive banner on **Home only** (it shows aggregates, no
  individual's data); a unit test pins the placement list so adding a placement is a deliberate review. The ads SDK receives no
  app data (plain `AdRequest`), banners are labelled "Advertisement", and a failed load collapses with no gap. No interstitials by design.
- **Manifest/privacy changes**: `INTERNET` + `ACCESS_NETWORK_STATE` added (for ads and billing only; AD_ID is merged in by the ads
  library). The in-app Privacy screen no longer says "no internet": it now explains ads, consent, purchases and that contacts are never
  uploaded. The Play Data-safety form and a public privacy-policy URL must say the same (checklist in `docs/PLAY_CONSOLE_SETUP.md`).
- Known gaps: no server-side receipt verification; no promo codes/intro-offer UI beyond what Play shows; the subscription uses the first
  base-plan offer; no A/B of placements; billing and ads code cannot run without your accounts so it is untested on a device.

## Phase 9 contents

- **App lock** (Settings -> Security): AndroidX `BiometricPrompt` with fingerprint/face and the device PIN/pattern/password as fallback
  (`BIOMETRIC_WEAK | DEVICE_CREDENTIAL`, one combination valid on every supported API level; availability is checked at runtime).
  Biometric data never reaches the app. Turning the lock on or off requires authenticating first, so a user cannot enable a lock they
  cannot pass. `MainActivity` is now a `FragmentActivity` (a `ComponentActivity` subclass) because BiometricPrompt requires it.
- **Auto-lock**: starts locked on every cold start; leaving the app starts a timer; returning after 15 s / 1 / 5 / 15 min (user choice,
  default 1 min) locks. The timer uses `SystemClock.elapsedRealtime` (monotonic) so changing the phone's date/time cannot bypass it, and a
  clock running backwards counts as "timed out". Rotation is not "leaving". The pure `AppLockManager` is unit-tested (10 tests).
  The shortest option is 15 s, not "immediately", so file pickers and permission dialogs (which stop the activity) don't force a re-login.
- **Lock screen**: opaque, swallows all touches, prompts automatically, "Unlock" button after a cancel. The app underneath stays
  composed (nav state is kept) but is removed from the accessibility tree while locked; the splash screen is held until the lock
  setting is known, so protected content can never flash before the lock. A notification tap still opens its contact after unlocking.
- **No lock-out trap**: if the device loses its screen lock / biometrics while app lock is on, the app can no longer authenticate the user.
  Staying locked would destroy access to their own data, so app lock switches itself off and tells the user (fail-open, by design,
  because the OS-level protection the lock relies on is gone).
- **Screen privacy** (default ON): `FLAG_SECURE` hides the app in the recents list and blocks screenshots/screen recording; toggle in
  Settings for users who want screenshots.
- **Privacy screen** (Settings -> Privacy): plain-language statement of what is stored, why each permission exists, OCR, sharing,
  backups and app lock. It currently says the app has no internet permission, no ads and no analytics; **Phase 10 (AdMob/Billing) must
  update this text, the manifest and the Play Data-safety answers in the same change.**
- **Erase all my data** (Settings): confirmation, then wipes contacts, follow-ups, tags, categories (built-ins re-created), card images,
  scan/restore caches, My card, and cancels reminders. Saved backup files are untouched.
- Limits stated honestly: app lock gates the UI; it does not encrypt the database (Android's app sandbox and device encryption protect
  data at rest). Rooted devices, a locked-out system and `adb` are out of scope.

## Phase 8 contents

- **Global search** (magnifier on Home): one box over contacts (name, company, title, phone, email, notes, tags, and category
  name in the current language) and follow-ups (title, notes, contact name/company). Every typed word must match (AND), debounced,
  grouped results, tap to open. Pure `SearchMatcher` is tested.
- **Backup format `vcardly-backup-v1`** (file extension `.vcbackup`): a ZIP containing `manifest.json` (first; format id, version,
  created time, app version, counts, SHA-256 of every other file), `data.json` (categories, tags, contacts incl. tag links and image
  references, follow-ups, my card) and `images/<uuid>.jpg`. Fields have defaults and unknown keys are ignored, so a future v1.x can
  add fields; a breaking change must bump the id. Newer-than-supported backups are refused with a clear message.
- **Optional password encryption** (recommended; default on): PBKDF2-HMAC-SHA256 (310k iterations) -> AES-256-GCM in 64 KiB chunks
  (STREAM construction): per-chunk nonce, header and "last chunk" flag authenticated, so bit flips, reordering and truncation are
  detected; memory use is flat. Tested: round trips at chunk boundaries, wrong password, every tamper type. A forgotten password cannot
  be recovered (the UI says so). Passwords are never stored and are wiped from memory after use.
- **Safe reading**: entry names are whitelisted (no zip-slip: `../`, absolute paths, sub-folders are ignored and never extracted),
  size caps per image/data/total (no zip bombs), manifest must come first, all checksums verified before anything is applied, field
  lengths capped on import, dangling references (category/tag/contact ids) dropped.
- **Restore** stages the entire file into a cache folder first (a wrong password or damaged file changes nothing), then:
  *Merge* = add what is missing (categories matched by system key / name, tags by name, duplicates skipped using the same detector as
  vCard import, follow-ups of skipped duplicates skipped too, images copied under fresh names, my card only filled if empty);
  *Replace everything* = one DB transaction wiping and re-inserting with original ids (confirmation dialog), then images swapped in;
  seeded categories are re-created if a backup lacks them. Reminders are re-armed afterwards.
- **Backup screen** (Settings -> Backup and restore): last backup time, password fields, system "create document" picker; restore flow
  with password prompt, summary of the file, mode choice, result counts.
- **Google Drive: NOT CONFIGURED, and nothing fakes it.** `CloudBackupProvider` is the seam; the only implementation,
  `UnconfiguredDriveProvider`, reports "not configured" and never touches the network. The Backup screen shows Drive as "Not set up in
  this build". The exact steps you must do (Cloud project, `drive.appdata` scope, Android OAuth client bound to your signing SHA-1,
  dependencies, enforcing encrypted uploads, adding INTERNET) are in `docs/GOOGLE_DRIVE_SETUP.md`. The app still declares **no
  INTERNET permission**.
- Known gaps: no scheduled/automatic backups (manual only); no "share backup" shortcut (use the picker to save to any folder, including
  a Drive or cloud-synced folder); the Android pieces (`BackupService` DB transaction, pickers, screens) are untested on a device;
  merge/replace do not carry the theme setting; only one pending restore at a time.

## Phase 7 contents

- **Reports and export** (Home button, or Settings): range chips (3 months / 12 months / all time).
  *Activity* (follows the range): contacts added, follow-ups completed / overdue, contacts-added-per-month column chart,
  follow-up completion bar, follow-ups by type. *Your contacts today* (always the whole library): totals, favorites,
  by category, how contacts were added (typed / scanned / imported), most-used tags. All numbers come from the real database
  through the pure, tested `ReportBuilder` (calendar months in the device zone, zero-filled, 36-month cap for All time).
- **Charts** are plain Compose (no chart library): one hue, thin rounded bars on a baseline, direct value labels, text in text
  colours, every bar has a spoken description ("June 2026: 3"). Category colours are deliberately not used for magnitude;
  the validator flagged some seeded category colours (slate "Other" reads gray, orange has low contrast on the surface), which is
  fine because they only mark identity next to a visible label, but is a reason not to build charts on them.
- **Export through the system file picker** (no storage permission; "wt" mode so overwriting never leaves stale bytes):
  - *PDF report*: summary, monthly bars, follow-up stats, category/source/tag bars and a contact directory table; A4, paginated,
    table header repeats per page, text truncated with ellipsis, mirrored for RTL locales. Content is built by a pure tested
    `ReportPdfContent`; `PdfDocRenderer` only draws it (platform `PdfDocument`, no library).
  - *CSV*: UTF-8 with BOM (Excel reads Unicode correctly), RFC 4180 quoting, and **formula-injection protection** (cells starting
    with `= + - @` get a leading `'`, except plain phone numbers like `+91 98765 43210`).
  - *Excel (.xlsx)*: two sheets (Contacts, Follow-ups), hand-written Office Open XML, inline strings only so nothing can run as a
    formula, bold frozen header, fitted column widths, XML-illegal control characters stripped.
  - Headers, yes/no, sources, statuses are localized from resources, so exports follow the app language.
- Known gaps: the PDF renderer has not been run on a device (layout may need tuning, e.g. long Telugu/Urdu names); no chart images in
  the PDF (bars are drawn as shapes, which is fine for print); exports are all-or-nothing (no per-category filter yet);
  the earlier "Import and export" screen handles vCard only, the Reports screen handles PDF/CSV/Excel.

## Phase 6 contents

- **My digital card** (Settings → My digital card, or the Home button): your own details (stored in DataStore, on-device), validated
  with the same rules as contacts, shown as a card with a **QR field picker** and "Share as contact file".
- **QR sharing for any contact** (share icon on contact details): tick which fields go in; the QR is regenerated live from exactly the
  ticked fields (ZXing core, pure Java, offline). NAME is always included (a vCard needs it). **Private notes can never be shared**
  (not offered, tested). Too-large selections show a message instead of crashing. QR is drawn dark-on-white in both themes.
- **vCard**: `VCardWriter` (3.0, CRLF, 75-octet folding that never splits a character, correct escaping, N/FN/ORG/TITLE/TEL/EMAIL/URL/ADR/
  NOTE/CATEGORIES/REV) and `VCardParser` (2.1/3.0/4.0 tolerant: unfolding, quoted-printable + charsets, group prefixes, bare types,
  fax dropped, mobile first, BOM, never throws; 5000-card and 5 MB limits). Unicode (Telugu/Hindi/Urdu) round-trips.
- **Import** (Settings → Import and export contacts): system file picker (no storage permission) → preview list → import selected.
  Duplicates (same email, same phone by last 9 digits, or same name+company, also within the file) are flagged and unticked. Values that
  fail form validation are kept in notes rather than lost; categories become tags; imported contacts are marked source=IMPORT.
- **Export all** via the system "create document" picker; includes notes, category and tags, after a confirmation that warns the file is private.
  Single-contact/My-card sharing uses the share sheet with a FileProvider URI to a cache file (replaced on each share).
- Known gaps: opening a `.vcf` from other apps ("Open with VCardly") is not wired yet; QR can be shown but not saved as an image; export is
  vCard only (PDF/CSV/Excel come in Phase 7); no QR *scanning* of other people's cards (the card scanner reads printed cards only).

## Phase 5 contents

- **Follow-ups tab**: Overdue / Today / Upcoming / Completed with live counts, complete / reopen / delete, add (contact picker with
  search). Buckets are derived from the clock; the flows re-compute after midnight while a screen is open. Contact details show and
  add that contact's follow-ups. Add/edit form: contact, title, type (call/email/meeting/message/other), date + time pickers
  (picker-UTC pitfall handled and tested), reminder on/off with lead time (at time / 15 min / 1 h / 1 day), notes, discard guard.
- **Reminder architecture**: `FollowUpManager` is the only writer (DB + alarm stay consistent). `AlarmReminderScheduler` arms one
  wall-clock `AlarmManager` alarm per follow-up (exact when the user allows it, otherwise `setAndAllowWhileIdle`). When the alarm fires
  `ReminderReceiver` re-reads the follow-up (it may be done/edited/deleted) before notifying, then records `notified_at`.
- **Survives reboot / time changes**: `SystemEventReceiver` handles BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED and
  exact-alarm permission changes by enqueuing a `RescheduleWorker` (WorkManager). The app also reschedules on every launch (force-stop
  clears alarms silently) and a 12-hour periodic safety-net worker runs. `ReminderPlanner` (pure, tested) decides: future -> arm;
  missed within 24 h -> show now; older -> mark handled, no spam (still visible under Overdue); already notified -> skip.
- **Notifications**: channel created at startup; lock screen shows only a generic "Follow-up reminder" (details marked private); tap opens
  the contact (deep link, consumed once so rotation doesn't re-open); "Mark done" action. `POST_NOTIFICATIONS` is requested when a
  reminder is switched on; Settings → Reminders shows notification / exact-alarm status with fix-it buttons.
- DB: `follow_ups.notified_at` added to schema v1 directly (no release existed yet, so no migration); from the first release every schema
  change needs a real Migration + committed schema JSON. Changing due time / lead time / reminder flag, or reopening, clears `notified_at`.
- Assumptions: due times are absolute instants (a zone change keeps the same instant rather than the same wall-clock hour);
  `SCHEDULE_EXACT_ALARM` is declared but not required (Google Play may ask for a justification in the app-content declaration; if you
  would rather not declare it, remove it and reminders become inexact);
  deleting a contact leaves its alarms to lapse harmlessly (the receiver ignores missing follow-ups); no snooze yet.

## Phase 4 contents

- **Flow**: Contacts → *Scan card* → capture (CameraX, or gallery picker) → crop/rotate → optional back side → OCR →
  **review form** (the normal add form, pre-filled) → save. Scan state lives in a `ScanSessionViewModel` scoped to the
  `scan` nav graph; Back steps through the flow.
- **OCR is never trusted**: a banner on the review form says to check every field; nothing is saved until the user taps Save;
  if OCR fails or finds nothing the form is empty and says so; lines no rule claimed are listed with "Add to notes".
  Raw OCR text is never persisted or logged.
- **`BusinessCardParser`** (pure Kotlin, tested): emails → websites (needs www/scheme or a known TLD, so `Pvt.Ltd` and `Dr.Rao`
  are not URLs) → phones (7–15 digits, mobile-labelled first, fax skipped but surfaced) → job-title keywords → company
  (suffix keywords, else the email/website domain, else most prominent line) → address (postal code / street keywords) →
  name (2–3 letters-only words, scored by text height, position and match with the email's local part).
- **Crop/rotate**: drag corners/move (pure `CropMath`, tested), rotate ±90°, "Whole image", retake. Rotation and crop are applied
  to the full-resolution capture, preview is downsampled.
- **Storage** (`CardImageStore`): permanent images in `filesDir/cards/<uuid>.jpg` (private, excluded from backup); work in progress in
  `cacheDir/scan`, wiped when the scan ends. Paths are UUIDs, traversal-checked. Images the user removes/replaces and images of
  deleted contacts are deleted. A failed image save blocks the contact save and tells the user.
- **Permissions**: `CAMERA` requested only when the scanner opens; denial offers settings or gallery import (no permission needed).
- ML Kit: bundled **Latin** model (works offline). **Telugu and Urdu scripts are not supported by ML Kit**; Hindi (Devanagari) would
  need the separate `text-recognition-devanagari` artifact. Cards in those scripts fall back to manual entry; the parser itself is
  script-agnostic apart from the keyword lists.
- Known gaps: crop handles are touch-only (alternatives: "Whole image"/rotate buttons); no torch/flash toggle; a scan in
  progress is lost if the process is killed (images are only in cache); cannot add images to an existing contact yet.

## Phase 3 contents

- **Contacts list**: debounced multi-token search (name, company, title, email, phone, notes, tag names), chips for
  Favorites / category / tags (tags AND together), 5 sorts, count, favorite toggle per row, FAB, distinct empty vs
  no-results states with "clear filters".
- **Details**: avatar/initials, category + tags, tap-to-call / email / website via system intents (no permissions;
  silently ignored when no handler), favorite, edit, delete with confirmation.
- **Add/Edit form**: single form for both; `ContactValidator` (pure, tested): name required, lengths, email, phone
  (5–15 digits, `+()-. ` allowed), website (scheme optional, IDN allowed). Errors are per-field, localized, exposed to
  TalkBack, cleared as the user edits. Unsaved-changes guard on Back/Up. Fields not shown on the form (images, source,
  created time, favorite) are preserved on edit. New tags are created inline (case-insensitive de-dup).
- **Categories & tags management** (Settings → Categories and tags): add/rename/delete custom categories (deleting
  uncategorises contacts), rename/delete tags with usage counts; seeded categories are read-only.
- Navigation: `contact/{id}`, `contact/edit/{id}` (0 = new), `organize`; saving a new contact replaces the form with its details.

## Phase 2 contents

- **Navigation** (`presentation/navigation`): single `NavHost` in `AppRoot`; bottom bar (Home, Contacts, Follow-ups, Settings)
  on top-level routes only, tab state saved/restored. Start destination is chosen after DataStore loads (splash held), so
  onboarding never flashes for returning users and finishing it does not rebuild the graph.
- **Onboarding**: 3-page pager (scan / private+offline / follow-ups), Skip, Next/Get started, page indicator announced to
  TalkBack; completion persisted in DataStore.
- **Dashboard**: live Room stats (total, favorites, added in last 30 days, follow-ups overdue/today/upcoming, per-category
  breakdown bars via tested `buildCategoryBreakdown`). Loading and empty states; stat cards deep-link to tabs. Numbers use
  locale-aware `NumberFormat`.
- **Settings**: Light/Dark/System radio group (DataStore, applies live), version, offline note.
- **Contacts / Follow-ups tabs**: honest "coming soon" placeholders until Phases 3 and 5.
- Dashboard "30 days" window and follow-up day buckets are fixed when the screen's ViewModel/flow is created; they refresh on
  next navigation, not at midnight (revisit in Phase 5).

## Phase 1 contents

- **Gradle**: version catalog (`gradle/libs.versions.toml`), AGP 8.7.3, Kotlin 2.0.21 (+Compose plugin), KSP,
  Hilt 2.52, Room 2.6.1, Compose BOM 2024.12.01, Gradle 8.14.3 wrapper. minSdk 26, target/compile 35, JVM 17.
  Release signing reads an optional git-ignored `keystore.properties` (see `.example`); without it the
  release build is unsigned. Room schemas export to `app/schemas`.
- **Design system** (`core/designsystem`): brand light/dark schemes, typography (system font so Telugu / Devanagari /
  Arabic render), shapes, spacing tokens, `VCardlyTheme(themeMode)`, components (buttons with 48dp targets, top bar with
  RTL-mirrored back icon, empty/error/loading states, stat card, section header, validated text field, confirm dialog).
- **Architecture**: `domain` (models + repository interfaces, no Android), `data` (repository impls),
  `core/database`, `core/datastore`, `di` (Hilt), `presentation`. `UiText` lets ViewModels emit strings without
  hardcoding; `AppResult/AppError`; `AppLog` (debug-only, documented no-PII rule); injectable `Clock` and dispatchers.
- **Room** (v1): `contacts`, `categories`, `tags`, `contact_tags` (many-to-many, cascade), `follow_ups`.
  Dynamic list query built by `ContactQueryBuilder` (bound args only, LIKE-escaped, multi-token AND search across
  fields + tag names, ALL-tags filter, favorites, category, 5 sorts). Dashboard/report count queries, follow-up bucket
  queries, reminder-candidate query for re-arming alarms. System categories seeded with blank names (localized in UI).
  No destructive migration fallback.
- **DataStore**: theme mode + onboarding flag behind `PreferencesRepository`.
- **Placeholder UI**: a themed screen proving DI/theme/strings; splash held until theme loads (no theme flash).
- Manifest: `allowBackup=false` and data-extraction rules exclude all data (PII); user backups are explicit (Phase 8).

## Assumptions & decisions

- Parser keyword lists are English/Latin; card layouts vary, so accuracy will be measured on real cards once the app runs.

- Phone validation is lenient on formatting but requires 5–15 digits; extensions ("ext 9") are rejected for now.
- Custom categories get a colour from a fixed palette (no colour picker yet); tag colours unused so far.
- Card images are not part of the form until Phase 4 (scanner); deleting a contact will also delete its image files then.

- Single Gradle module for now; package-level layering. Split later only if build times demand it.
- Dynamic (wallpaper) color disabled so brand contrast is guaranteed.
- Follow-up persisted status is only `PENDING`/`COMPLETED`; Today/Upcoming/Overdue are derived from the clock.
  Day buckets use the device zone when a flow is created (Phase 5 will refresh on date/zone change).
- Contact name is a single `full_name` field (matches what OCR can reliably produce).
- System categories store a `system_key` and a blank name so they localize; custom categories store user text.
- Image paths are stored relative to app-private storage (implemented in Phase 4).
- Dependency versions are known-good older releases chosen without being able to resolve latest; bump after the first
  successful build.
- Raw OCR text is never persisted (PII minimisation); only user-confirmed fields are saved.

## Needs external configuration (never faked)

| Service | Status |
|---|---|
| Release signing | Needs your keystore → `keystore.properties` |
| Google Drive backup | Needs OAuth client / Cloud project (`docs/GOOGLE_DRIVE_SETUP.md`); seam only, always "not configured" |
| Play Billing | Needs Play Console products `vcardly_pro_lifetime` + `vcardly_pro_yearly` and a testing-track install (`docs/PLAY_CONSOLE_SETUP.md`) |
| AdMob | Needs your App ID + banner unit ID in `secrets.properties` and a published consent message; test IDs in debug, **ads OFF in release** until then |
