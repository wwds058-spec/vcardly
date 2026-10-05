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
| `ContactQueryBuilderTest` (6) + `CategoryBreakdownTest` (3) on the JVM | 9/9 pass (the first caught a real bug, fixed) |
| Generated search SQL executed against real SQLite with the entity schema | OK |
| Everything else (Gradle/AGP config, Room/KSP, Hilt, Compose, resources, manifest, androidTest) | **Unverified** |

First action on a machine with Google Maven access: `./gradlew :app:assembleDebug :app:testDebugUnitTest`,
fix any real errors, then commit the generated `app/schemas/**/1.json` (Room exports it on first build).
`androidTest/DatabaseTest` needs an emulator/device: `./gradlew :app:connectedDebugAndroidTest`.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | Gradle, design system, architecture, Room foundation | Written, **not build-verified** |
| 2 | Onboarding, navigation shell, dashboard (real DB stats), settings/theme | Written, **not build-verified** |
| 3 | Contacts list (search/filter/sort), categories, tags, favorites, details, add/edit + validation | Planned |
| 4 | Image storage, CameraX scanner, crop/rotate, ML Kit OCR + heuristic parsing + review screen | Planned |
| 5 | Follow-ups + notifications (boot / time-change safe) | Planned |
| 6 | Digital card, QR, vCard export/import | Planned |
| 7 | Reports + PDF/CSV/Excel export | Planned |
| 8 | Global search, backup/restore (`vcardly-backup-v1`), Drive preparation | Planned |
| 9 | Biometric lock + auto-lock, privacy screen | Planned |
| 10 | EntitlementManager, Play Billing, AdMob (test IDs) | Planned |
| 11 | EN/TE/HI/UR localization + RTL, accessibility pass, release hardening | Planned |

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
