# VCardly architecture

This document has two parts: [Part 1 – UI layer](#part-1--ui-layer) and
[Part 2 – Data layer](#part-2--data-layer).

# Part 1 – UI layer

The UI is a single-Activity Compose app with MVVM and one-way data flow. Paths below are under
`app/src/main/kotlin/com/yasin/vcardly/`.

## 1. Layers

```
MainActivity (single Activity, edge-to-edge)
 └─ VCardlyTheme  (Light / Dark / System, read from DataStore)
     └─ Lock gate (LockScreen overlays everything while locked)
         └─ AppRoot  (Scaffold + NavHost + bottom bar)
             └─ Screen ⇄ ViewModel ⇄ Repository / Manager ⇄ Room · DataStore · system APIs
```

| Layer | Package | Responsibility |
|---|---|---|
| Design system | `core/designsystem/{theme,component}` | Color, Type, Shape, Spacing, a WCAG `Contrast` helper, and shared components (Buttons, Cards, FormFields, States, TopBar) |
| Navigation | `presentation/navigation` | `Routes`, `TopLevelDestination`, and `AppRoot` |
| Features | `presentation/<feature>` | One folder per feature, each with a `*Screen.kt` and a `*ViewModel.kt` |
| Shared UI | `presentation/common` | Avatar, card image view, form field, ad banner, validation text |
| Domain | `domain/*` | Pure Kotlin models, repository interfaces and logic (parser, planner, report builder) |
| Data and core | `data/repository`, `core/*` | Room, DataStore, camera/OCR, notifications, billing, ads, backup |

## 2. Screen pattern

```
ViewModel (@HiltViewModel)
  private MutableStateFlow / repository Flows
  └─ combine(...) → StateFlow<XxxUiState>      (stateIn, WhileSubscribed)
  └─ fun onEvent / onXxxChanged(...)           (user actions)
  └─ one-shot effects via Channel / SharedFlow (snackbars, saved, share)

Screen composable
  val state by vm.uiState.collectAsStateWithLifecycle()
  stateless content composables ← state + lambdas
  navigation callbacks passed in from AppRoot
```

- **Screens don't know about the NavController.** `AppRoot` passes them lambdas such as
  `onOpenContact`, `onNavigateUp` and `onSaved`.
- **UI state is an immutable data class per screen.** For example, `ContactsUiState` holds
  `isLoading`, `filter`, `contacts`, `categories` and `tags`.
- **Searching is debounced in the ViewModel.** `ContactsViewModel` debounces the filter and uses
  `flatMapLatest` into `observeContacts`.
- **The database drives the UI.** Room returns Flows, so lists update live without manual refreshes.
- **Loading, empty and error states use shared components** from `States.kt`.
- **Each screen exposes a stateless `XxxContent(state, actions)`** (for example `DashboardContent`,
  `ContactsContent`, `ContactEditContent`). The `XxxScreen` wrapper collects the ViewModel and wires the
  callbacks; tests and screenshots render the content directly with sample state.

## 3. Navigation graph

Bottom bar (4 top-level tabs): Home, Contacts, Follow-ups, Settings. The bar is shown only on these
routes; tab switches use `navigateTopLevel` (save/restore state, single top). A raised **Scan** button
sits in the middle of the bar. It is an action, not a fifth destination: it calls the shared
`rememberScanLauncher` (free-plan scan allowance check) and then opens the scan graph. Home's quick
action and the Contacts empty state use the same launcher.

```
onboarding ─► home
home ─► mycard (+ edit), reports, search, contacts, followups
contacts ─► contact/{id} ─► contact/edit/{id}?fromScan, qr/{id}, followup/edit/{id}?contactId
         ├─► scan (nested graph): scan/capture ─► scan/crop ─► contact/edit/0?fromScan=true
         └─► pro
followups ─► followup/edit/{id}
settings ─► organize, backup, transfer, privacy, pro
```

- **The scan flow is a nested graph.** Capture and crop share one `ScanSessionViewModel`, scoped to
  the graph's back-stack entry, so the image and crop state survive between the two screens. The
  OCR review is `contact/edit/0?fromScan=true` above the graph; its "Rescan" calls
  `ScanSessionViewModel.restart()` and pops back to capture. After saving, `popUpTo(SCAN_GRAPH)`
  removes the whole flow.
- **Transitions:** tab switches cross-fade; pushed screens fade in with a small upward slide.
- **Reminder notification taps** arrive as `openContactId` and are routed to the contact details once
  onboarding is done.
- **The start destination** is chosen before the first frame (`onboardingCompleted`), so there is no
  flicker.

## 4. Cross-cutting UI concerns

- **Theme:** `VCardlyTheme` reads the Light/Dark/System setting from DataStore. Avatar text color is
  picked per background using `Contrast`, which has a unit test.
- **Strings:** all user-facing text comes from `stringResource`; no hardcoded strings. English only,
  though RTL support stays enabled.
- **Accessibility:** bottom-bar icons are decorative because the labels are always shown. Other icons
  carry content descriptions.
- **App lock:** a `LockScreen` sits above the nav host, driven by `AppLockManager` with a monotonic
  clock for auto-lock.
- **Ads and Pro:** `AdBanner` appears on Home only. It is gated by `EntitlementManager`, so it never
  shows for Pro users or while locked.
- **Permissions and system pickers:** camera, notifications and exact-alarm permission go through
  activity-result launchers. Exports, backups and imports use the system file picker (SAF).
- **Insets:** each screen owns its top bar and status-bar insets. `AppRoot` applies only the
  bottom-bar padding.

## 5. Design system

`core/designsystem` holds everything visual that is not tied to a domain model:

| Piece | Contents |
|---|---|
| `theme/Color.kt` | Material light scheme and a separately designed deep-navy dark scheme; `VCardlyColors` with tone families (blue, rose, mint, orange, lavender, navy: container, ink, accent), CTA colour, gradients. Read with `MaterialTheme.vcColors`. Pairs are checked by `ThemeContrastTest`. |
| `theme/Type.kt` | Plus Jakarta Sans (bundled, OFL) with system fallback for non-Latin scripts; `StatValueStyle`, `OverlineStyle` |
| `theme/Spacing.kt`, `Shape.kt` | 4dp grid, 20dp screen margin, elevation tokens; pill, card, tile and sheet shapes |
| `component/` | buttons (primary pill, secondary, tonal, text, icon, FAB), cards (`VCardlyCard`, stat card, gradient action tile, navigation row, group), search bar and chips, avatar, info row, timeline, stepper, top bars, bottom navigation, logo, empty/loading/error states and notices |

Components that take domain models live in `presentation/common` (`VCardlyContactCard`,
`VCardlyFollowUpCard`, `VCardlyDigitalCard`, `BusinessCardArt`, `CardImageView`, `ExternalActions`), so
the design system never depends on the domain layer. Screens draw on the app background (no `Surface`),
so `VCardlyTheme` provides `LocalContentColor`.

## 6. Where things live

| I want to change… | Look in |
|---|---|
| Colors, type, spacing | `core/designsystem/theme/` |
| A reusable button, card or form field | `core/designsystem/component/` |
| Routes or the tab bar | `presentation/navigation/` |
| One feature's screen or logic | `presentation/<feature>/` |
| What data a screen shows | `domain/repository/` and `data/repository/` |

# Part 2 – Data layer

The data layer is offline-first: everything lives on the device. There is no server, and no
network call is needed to read or write user data. Paths are under
`app/src/main/kotlin/com/yasin/vcardly/`.

## 1. Layers and dependency direction

```
presentation (ViewModels)
      │  depend on interfaces only
      ▼
domain/repository  ── ContactRepository, CategoryRepository, TagRepository,
      ▲                FollowUpRepository, PreferencesRepository, MyCardRepository
      │  implemented by (bound in di/RepositoryModule)
      │
data/repository  ── Room-backed:      ContactRepositoryImpl, CategoryRepositoryImpl,
core/datastore                        TagRepositoryImpl, FollowUpRepositoryImpl
                 ── DataStore-backed: PreferencesRepositoryImpl, MyCardRepositoryImpl
      │
      ▼
storage: Room (vcardly.db) · DataStore (vcardly_preferences) · app-private files (filesDir/cards)
```

- **The domain layer is pure Kotlin.** It contains models, repository interfaces and logic, with
  no Android or Room imports, so it is unit-tested on the JVM.
- **Entities never leave the data layer.** `core/database/mapper/Mappers.kt` converts between Room
  entities and domain models (`toDomain()` / `toEntity()`).
- **Hilt wires everything as singletons.** `DatabaseModule` provides the database and DAOs,
  `CoreModule` provides `DataStore`, `Clock` and `AppDispatchers`, and `RepositoryModule` binds the
  interfaces to their implementations.
- **Time comes from an injected `java.time.Clock`**, so day boundaries and timestamps are testable.

## 2. Storage at a glance

| Store | What it holds | Where |
|---|---|---|
| Room database `vcardly.db` | Contacts, categories, tags, contact↔tag links, follow-ups | `core/database` |
| Preferences DataStore `vcardly_preferences` | Theme, onboarding done, app lock, auto-lock seconds, secure screen, cached Pro flag, last backup time, the user's own card ("My card") | `core/datastore` |
| App-private files | Card photos in `filesDir/cards`; scan work-in-progress in `cacheDir/scan`; restore staging in `cacheDir` | `core/image/CardImageStore` |
| User-chosen files (SAF) | Backups, vCard and CSV/XLSX/PDF exports, imports | `core/backup`, `core/export`, `presentation/transfer` |

Android auto-backup is off (`allowBackup="false"` plus `data_extraction_rules.xml`). Data leaves
the device only through an explicit user action: a backup, an export or a share.

## 3. Room schema (version 1)

```
categories ──< contacts >──< contact_tags >── tags
                   │
                   └──< follow_ups
```

| Table | Key columns | Constraints and indexes |
|---|---|---|
| `categories` | `id`, `name`, `color_argb`, `system_key`, `sort_order`, `created_at` | unique `system_key`. System categories are seeded on create with a blank name (the UI shows a translated label); categories added in later versions are appended on open with `INSERT OR IGNORE` (no schema change) |
| `tags` | `id`, `name` (NOCASE), `color_argb`, `created_at` | unique `name`, case-insensitive |
| `contacts` | name, job title, company, 2 phones, 2 emails, website, address, notes, `category_id`, `is_favorite`, `front/back_image_path`, `source` (MANUAL/SCAN/IMPORT…), `created_at`, `updated_at` | FK `category_id → categories` **ON DELETE SET NULL**; indexes on `category_id`, `is_favorite`, `created_at` |
| `contact_tags` | `contact_id`, `tag_id` (composite PK) | both FKs **ON DELETE CASCADE**; index on `tag_id` |
| `follow_ups` | `contact_id`, `type`, `status`, `title`, `notes`, `due_at`, `reminder_enabled`, `reminder_offset_minutes`, `completed_at`, `notified_at`, timestamps | FK `contact_id → contacts` **ON DELETE CASCADE**; indexes on `contact_id` and `(status, due_at)` |

- **Enums are stored by name.** `Converters` falls back to a safe default for unknown values (for
  example from a newer backup) instead of failing the whole query. Follow-up statuses: `PENDING` and
  `RESCHEDULED` are active (Today/Upcoming/Overdue, reminders), `COMPLETED` and `CANCELLED` are closed
  (the Completed tab). Types: call, WhatsApp, email, meeting, quotation, payment, other (`MESSAGE` kept
  for older data).
- **Read models** use `@Relation`: `ContactWithRelations` (contact + category + tags through the
  junction) and `FollowUpWithContactEntity`, loaded inside `@Transaction` queries.
- **Migrations:** the schema JSON is exported to `app/schemas/`, and every change must add a
  `Migration` to `ALL_MIGRATIONS`. Destructive fallback is deliberately **not** enabled, because
  contacts are irreplaceable user data.

## 4. Reads: reactive Flows

- **Every list and counter is a `Flow` from Room**, so the UI updates on any write with no manual
  refresh.
- **Contact search and filter** go through `ContactQueryBuilder`, which builds one SQL query:
    - each whitespace-separated token must match a name, company, title, email, phone, notes or
      tag name;
    - category, favorites-only and "has all these tags" filters are ANDed in;
    - sorting comes from a closed `ContactSort` enum;
    - user input only reaches SQLite as **bound arguments** with `LIKE` wildcards escaped, so it
      can't inject SQL. The builder is pure Kotlin and unit-tested.
- **Follow-up buckets** (Overdue, Today, Upcoming, Completed) are computed in SQL from day bounds in
  the device's time zone. `FollowUpRepositoryImpl.dayBoundsFlow()` re-emits just after midnight, so
  an open screen rolls over to the new day by itself.
- **Dashboard and report stats** combine several count Flows: total, favorites, added since, by
  category, and the follow-up bucket counts.

## 5. Writes: transactions and side effects

| Operation | How it stays consistent |
|---|---|
| Save contact + tags | `db.withTransaction { insert/update; clear tag links; insert tag links }`, with timestamps from `Clock` |
| Delete contact | One delete; FKs cascade to tag links and follow-ups. Category deletion sets contacts' category to null |
| Delete category | Only custom ones (`DELETE … WHERE system_key IS NULL`) |
| Tags | `findOrCreate` is case-insensitive; rename uses `UPDATE OR IGNORE` so a clash is reported, not crashed |
| Follow-ups | **All writes go through `FollowUpManager`**, which saves to the DB and then schedules or cancels the OS alarm, so the stored state and the alarm never disagree |
| Card images | `CardImageStore.persist()` writes to a `.tmp` file and renames it, so a crash never leaves a half-written image. Only relative paths are stored in the DB |
| Erase all data | `DataEraser` deletes all rows and re-seeds the system categories in one transaction, deletes card images and caches, clears My card, then cancels every alarm. Settings are kept |

## 6. Reminders and the database

```
FollowUpManager ──► FollowUpRepository (Room)
       └──────────► ReminderScheduler (AlarmManager, one alarm per follow-up)

Boot / time change / time-zone change / app start / every 12 h
   └─► RescheduleWorker ──► getReminderCandidates() (PENDING + reminder on)
                            ──► ReminderPlanner decides what to (re)schedule
ReminderReceiver ──► getWithContact(id) ──► notification ──► markNotified(id)
```

The database is the source of truth; alarms are always rebuilt from it, which is why reminders
survive reboots and clock changes.

## 7. Backup and restore (`vcardly-backup-v1`)

- **Format:** a zip with `manifest.json` first (format version, counts, a SHA-256 for every entry),
  `data.json` (categories, tags, contacts, links, follow-ups, My card) and the card images under
  `images/`. Size caps: 128 MB data, 30 MB per image, 4 GB total, 50,000 entries.
  Optionally password-encrypted with PBKDF2 + AES-256-GCM in chunks (`ChunkedAesGcm`).
- **Create:** `BackupService.create()` takes a snapshot of all tables, writes the archive to a
  file the user picked, and records `last_backup_at`.
- **Restore is staged and verified before touching the DB:**
    - newer format versions, a missing or wrong password, checksum mismatches, path traversal
      (zip-slip) and oversize archives are all rejected with a typed `BackupFailure`;
    - **Replace** wipes and reinserts everything in one transaction;
    - **Merge** uses `RestorePlanner` (pure Kotlin) to remap IDs, match categories and skip
      duplicate contacts;
    - reminders are rescheduled afterwards.
- **Google Drive** sits behind `CloudBackupProvider`. The current implementation is
  `UnconfiguredDriveProvider`, which honestly reports that Drive isn't set up yet (see
  `docs/GOOGLE_DRIVE_SETUP.md`).

## 8. Other data sources

| Source | Component | Notes |
|---|---|---|
| Camera + OCR | `CameraX` → `CardImageStore` → `OcrEngine` (ML Kit, on-device) → `BusinessCardParser` | The parsed result is only a draft; the user always reviews it before saving |
| vCard import/export | `domain/vcard` (`VCardParser`, `VCardWriter`, `VCardImporter`) | Imported contacts go through the normal repository save |
| Reports and exports | `ReportBuilder` → `CsvWriter` / `XlsxWriter` / `PdfDocRenderer` | Written to a user-picked file |
| Entitlements | `PlayBillingRepository` → `EntitlementManager` | Pro is cached in DataStore and reconciled with Google Play on each query |

## 9. Privacy rules in the data layer

- No personal data is logged: no names, phones, emails or file paths.
- All storage is app-private; nothing is world-readable.
- Card photos are shared only through `FileProvider` with temporary read permission.
- The secure-screen preference adds `FLAG_SECURE` to block screenshots.

## 10. Testing

| What | Where |
|---|---|
| Query builder, validators, parser, planners, vCard, backup archive, crypto, restore planner (JVM) | `app/src/test` |
| Room DAOs and repositories on a real SQLite database (emulator) | `app/src/androidTest`: `DatabaseTest`, `ContactRepositoryTest`, `FollowUpRepositoryTest` |
| UI interactions on the stateless screen content | `app/src/androidTest/.../ui/ScreenBehaviourTest` |
| Screenshots of every redesigned screen, light and dark, published by CI to the `ui-screenshots` branch | `app/src/androidTest/.../ui/ScreenshotTest` |
| Theme contrast, WhatsApp number handling | `ThemeContrastTest`, `WhatsAppDigitsTest` (JVM) |
