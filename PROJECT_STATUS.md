# VCardly — Project Status

Offline-first Android business-card manager. Package `com.yasin.vcardly`, single `:app` module,
Kotlin + Compose + Material 3, MVVM / clean layering, Hilt, Room, DataStore.

## Build verification — READ THIS FIRST

**Phase 1 has NOT been built with Gradle/AGP.** The authoring environment's network policy blocks
`dl.google.com` (Google Maven + Android SDK), so AGP, AndroidX, Compose, Room, and the Android
platform jar could not be resolved. What *was* verified:

| Item | Result |
|---|---|
| `ContactQueryBuilder` + domain models compiled with Kotlin 2.0.21 on the JVM | OK |
| Pure-Kotlin JVM tests (query builder, category breakdown, validators, initials, card parser, crop math, reminder planner, due time, vCard, QR round trip, reports, CSV, XLSX, backup archive, chunked AES-GCM, restore planner, search matcher) | 116/116 pass (tests caught three real bugs, fixed) |
| Generated `.xlsx` opened with `openpyxl` (independent reader) | OK: sheets, bold frozen header, Unicode, text-only cells |
| Generated search SQL executed against real SQLite with the entity schema | OK |
| Everything else (Gradle/AGP config, Room/KSP, Hilt, Compose, resources, manifest, androidTest) | **Unverified** |

First action on a machine with Google Maven access: `./gradlew :app:assembleDebug :app:testDebugUnitTest`,
fix any real errors, then commit the generated `app/schemas/**/1.json` (Room exports it on first build).
`androidTest/DatabaseTest`, `ContactRepositoryTest` and `FollowUpRepositoryTest` need an emulator/device: `./gradlew :app:connectedDebugAndroidTest`.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | Gradle, design system, architecture, Room foundation | Written, **not build-verified** |
| 2 | Onboarding, navigation shell, dashboard (real DB stats), settings/theme | Written, **not build-verified** |
| 3 | Contacts list (search/filter/sort), categories, tags, favorites, details, add/edit + validation | Written, **not build-verified** |
| 4 | Image storage, CameraX scanner, crop/rotate, ML Kit OCR + heuristic parsing + review screen | Written, **not build-verified** |
| 5 | Follow-ups + notifications (boot / time-change safe) | Written, **not build-verified** |
| 6 | Digital card, QR, vCard export/import | Written, **not build-verified** |
| 7 | Reports + PDF/CSV/Excel export | Written, **not build-verified** |
| 8 | Global search, backup/restore (`vcardly-backup-v1`), Drive preparation | Written, **not build-verified** (Drive: **needs your configuration**) |
| 9 | Biometric lock + auto-lock, privacy screen | Planned |
| 10 | EntitlementManager, Play Billing, AdMob (test IDs) | Planned |
| 11 | EN/TE/HI/UR localization + RTL, accessibility pass, release hardening | Planned |

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
| Google Drive backup | Needs OAuth client / Cloud project (Phase 8 prepares the interface only) |
| Play Billing | Needs Play Console products (Phase 10) |
| AdMob | Needs your App ID / unit IDs; Google test IDs only until then (Phase 10) |
