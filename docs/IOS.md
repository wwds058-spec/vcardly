# VCardly for iPhone (native SwiftUI)

A native iOS app in `ios/` with the same design system, screens and rules as the Android app. It is a separate codebase (Swift),
not a port of the Kotlin code; the domain logic (business-card parser, validator, vCard writer, follow-up buckets and statuses) is
ported line by line and covered by the same test cases.

## Stack

| Concern | Android | iOS |
|---|---|---|
| UI | Jetpack Compose, Material 3 | SwiftUI (iOS 17+) |
| State | ViewModel + StateFlow | `@Observable` `AppEnvironment`, `DataRevision` bumped after every write, screens reload with `.task(id:)` |
| Storage | Room | SwiftData (`ContactEntity`, `CategoryEntity`, `TagEntity`, `FollowUpEntity`), app-private Application Support |
| Settings, My card | DataStore | `UserDefaults` (`Preferences`) |
| Card photos | app-private files | `Application Support/cards`, atomic writes, excluded from iCloud backup, data protection |
| OCR | ML Kit (on device) | Apple Vision `VNRecognizeTextRequest` (on device) + the ported `BusinessCardParser` |
| Scanner | CameraX + manual crop | VisionKit document camera (edge detection and perspective crop; page 2 = back of card), or a photo from the library |
| Reminders | AlarmManager / WorkManager | `UNUserNotificationCenter` calendar triggers (soonest 60, re-armed at launch) |
| App lock | AndroidX Biometric | LocalAuthentication (`deviceOwnerAuthentication`: Face ID / Touch ID / passcode), monotonic uptime timer |
| Charts | custom Canvas | Swift Charts |
| Backup / restore | `BackupArchive` (java.util.zip), `ChunkedAesGcm` | `BackupArchive` + own `ZipArchive` (Foundation has no ZIP API), `BackupCrypto` (CommonCrypto PBKDF2 + CryptoKit AES-GCM); **same file format, files move between platforms** |
| Fonts | Plus Jakarta Sans in `res/font` | same four weights in `Resources/Fonts`, `UIAppFonts`, Dynamic Type via `relativeTo:` |
| Strings | `strings.xml` | `Resources/en.lproj/Localizable.strings` (`L10n.s` / `L10n.plural`) |

## Layout

```
ios/
  project.yml                 XcodeGen spec (the .xcodeproj is generated, not committed)
  VCardly/
    App/                      VCardlyApp, AppEnvironment (+ AppLock), RootView (tabs, centre Scan action, routes)
    DesignSystem/             Theme (tokens, tones, gradients, fonts, vcCard), Components, FormFields
    Domain/                   Models, L10n, ContactValidator, BusinessCardParser, VCard
    Data/                     SwiftData entities, repositories, CardImageStore, ReminderScheduler, Preferences
    Features/                 Home, Contacts (list, detail, edit), FollowUps, MyCard (+ QR share), Reports, Settings,
                              Search (+ Privacy, Pro), Onboarding (+ Lock), Scan
    Resources/                Fonts (OFL), Assets (app icon, launch colour), en.lproj/Localizable.strings
  VCardlyTests/               parser, validator, vCard, resources, repositories (in-memory store), screenshot renders
```

Every screen follows the Android pattern: a thin `XxxScreen` that reads the environment and passes plain state and closures to a
stateless `XxxContent` view. Screens never navigate; the shell (`MainShell`) owns one `NavigationStack` per tab and the scan
full-screen cover.

## Build and test

Needs a Mac with Xcode 16 or later.

```
brew install xcodegen
cd ios && xcodegen generate
open VCardly.xcodeproj            # run on a simulator, or on a device after choosing your team
xcodebuild test -project VCardly.xcodeproj -scheme VCardly -destination 'platform=iOS Simulator,name=iPhone 16 Pro' CODE_SIGNING_ALLOWED=NO
```

CI (`ios` job in `.github/workflows/ci.yml`, `macos-15` runner) generates the project, builds, runs all tests and publishes the
rendered screens (light and dark, fictional sample data) to the `ios-screenshots` branch. macOS runner minutes are billed at a
higher rate than Linux on private repositories.

## Backup and restore

Settings → Backup & restore. Same `vcardly-backup-v1` file as Android (see `docs/ARCHITECTURE.md`, "Backup and restore"), so a
backup made on an Android phone restores on an iPhone and the other way round.

- **Create**: optional password (at least 8 characters; PBKDF2-HMAC-SHA256 with 310,000 iterations, AES-256-GCM in authenticated
  64 KiB chunks). The file is written to a private temporary folder, handed to the system "Save to Files" sheet, then deleted.
- **Restore**: pick any file. It is decrypted and every entry is checked (manifest first, SHA-256 of every file, size caps, no
  path traversal, no ZIP64) into a private staging folder before anything changes. Then **Add to what I have** (skips contacts
  you already have: same email, same phone's last 9 digits, or same name + company) or **Replace everything** (confirmed first).
  The database is saved in one go and rolled back, with copied photos removed, if that fails. Reminders are re-armed afterwards.
- **Proof of compatibility**: `tools/backup_reference.py` is a small reference implementation of the format. It wrote the fixtures
  in `testdata/backup/` (shaped like Android's `java.util.zip` output); Android's `BackupFixtureCompatTest` and the iOS
  `BackupTests` both restore them, and CI checks the backups written by the iOS tests with the same script.
- Restore needs free space for one decrypted copy of the backup while it runs.

## Import and export

Settings → Import & export, the counterpart of Android's Transfer screen, using a port of the same vCard reader and importer:

- **Import a .vcf file** (up to 5 MB) or **pick people from iPhone Contacts** with Apple's contact picker. The picker needs
  no address-book permission: only the people you pick are handed over, read-only, and their notes are not read.
- Everything goes through one review list: contacts you already have (same email, phone or name + company, also within the
  file) and cards with no usable details are not selected; values the form would reject are kept in the notes, never dropped.
- **Export all contacts** to one .vcf (every field, including private notes, category and tags as CATEGORIES), after a warning.

## Report exports

Reports → Export: **PDF** (summary, new contacts per month, follow-ups by status, categories, top tags and a contact directory,
drawn on A4 with `UIGraphicsPDFRenderer`), **CSV** (contacts; RFC 4180, UTF-8 BOM, formula injection neutralised like Android)
and **Excel** (.xlsx with Contacts and Follow-ups sheets; every cell is plain text, header bold and frozen). Same columns as
Android. Files are written to a private temporary folder, saved with the Files sheet, then deleted. CI opens the files written
by the tests with Python's `csv` module and `openpyxl` (`tools/verify_exports.py`).

## Needs your configuration (never faked)

| What | Status | What you need to do |
|---|---|---|
| Apple Developer account, signing | not set up; `DEVELOPMENT_TEAM` is empty and CI builds with signing off | join the Apple Developer Program, set your team in Xcode (or `project.yml`), create the App ID `com.yasin.vcardly` |
| In-app purchases (Pro) | the Pro screen says purchases are not set up and sells nothing | create products in App Store Connect, then add StoreKit 2 purchase code |
| TestFlight / App Store | not prepared | App Store Connect record, privacy nutrition label (no data collected), screenshots, review notes. Export compliance: backups use only Apple's built-in encryption (CryptoKit, CommonCrypto) to protect the user's own data; `ITSAppUsesNonExemptEncryption` is set to `false` on that basis, so confirm it when you submit |

## Differences from Android (by design or not yet built)

- **No ads on iPhone** (AdMob is not included).
- **Google Drive** (direct): not built. Backups and exports are saved with the Files sheet, so iCloud Drive, Google Drive's
  Files provider or any other location works without setup.
- **PDF and Excel exports are free on iPhone** for now (on Android they need Pro), because purchases are not set up on iOS.
  `ProFeatures.exportsRequirePro` locks them like Android once StoreKit purchases exist.
- **Free-plan scan quota**: not enforced on iOS (no purchases exist yet).
- Scanning uses Apple's document camera instead of a custom camera screen; on devices without it (and in the simulator) the user
  picks a photo instead.
- Never verified on a real iPhone: camera, OCR on real cards, notifications on the lock screen, Face ID.
