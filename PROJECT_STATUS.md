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
| Pure-Kotlin JVM tests: query builder (6), category breakdown (3), contact validator (7), initials (6), business-card parser (7), crop math (4) | 33/33 pass (the query-builder test caught a real bug, fixed) |
| Generated search SQL executed against real SQLite with the entity schema | OK |
| Everything else (Gradle/AGP config, Room/KSP, Hilt, Compose, resources, manifest, androidTest) | **Unverified** |

First action on a machine with Google Maven access: `./gradlew :app:assembleDebug :app:testDebugUnitTest`,
fix any real errors, then commit the generated `app/schemas/**/1.json` (Room exports it on first build).
`androidTest/DatabaseTest` and `ContactRepositoryTest` need an emulator/device: `./gradlew :app:connectedDebugAndroidTest`.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | Gradle, design system, architecture, Room foundation | Written, **not build-verified** |
| 2 | Onboarding, navigation shell, dashboard (real DB stats), settings/theme | Written, **not build-verified** |
| 3 | Contacts list (search/filter/sort), categories, tags, favorites, details, add/edit + validation | Written, **not build-verified** |
| 4 | Image storage, CameraX scanner, crop/rotate, ML Kit OCR + heuristic parsing + review screen | Written, **not build-verified** |
| 5 | Follow-ups + notifications (boot / time-change safe) | Planned |
| 6 | Digital card, QR, vCard export/import | Planned |
| 7 | Reports + PDF/CSV/Excel export | Planned |
| 8 | Global search, backup/restore (`vcardly-backup-v1`), Drive preparation | Planned |
| 9 | Biometric lock + auto-lock, privacy screen | Planned |
| 10 | EntitlementManager, Play Billing, AdMob (test IDs) | Planned |
| 11 | EN/TE/HI/UR localization + RTL, accessibility pass, release hardening | Planned |

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
