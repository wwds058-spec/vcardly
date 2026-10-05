# VCardly

Your smart digital visiting-card book. Scan a visiting card, review what was read, and keep the contact together with the original
card image; then call, WhatsApp or email, organise with categories and tags, plan follow-ups with reminders, and share your own
digital card as a QR code. Offline-first: everything stays on your device.

Kotlin, Jetpack Compose (Material 3), MVVM / clean layering, Hilt, Room, DataStore, Coroutines/Flow, CameraX, ML Kit text
recognition, AndroidX Biometric, WorkManager + AlarmManager, Play Billing, AdMob. Package `com.yasin.vcardly`, minSdk 26, target 35.
An iPhone version (SwiftUI, iOS 17+) with the same design lives in `ios/` — see [docs/IOS.md](docs/IOS.md).
English only (right-to-left layouts are supported by the manifest and Compose, but no translations ship).

| Document | What it covers |
|---|---|
| [PROJECT_STATUS.md](PROJECT_STATUS.md) | What is built, last build/test results, what needs your configuration, known limitations |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | UI and data architecture, design system (the technical source of truth) |
| [PRIVACY_AND_SECURITY.md](PRIVACY_AND_SECURITY.md) | Where data lives, permissions, protections, logging rules |
| [PLAY_STORE_CHECKLIST.md](PLAY_STORE_CHECKLIST.md) | Release and store-listing steps |
| [docs/IOS.md](docs/IOS.md) | The native SwiftUI iPhone app in `ios/`: stack, build, tests, what needs your Apple account |
| [docs/PLAY_CONSOLE_SETUP.md](docs/PLAY_CONSOLE_SETUP.md), [docs/GOOGLE_DRIVE_SETUP.md](docs/GOOGLE_DRIVE_SETUP.md) | Billing, AdMob and Drive setup |

## Build and test

Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir=...`, or `ANDROID_HOME`).

```
./gradlew :app:assembleDebug          # debug APK (uses Google's test ad IDs)
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintDebug              # Android lint
./gradlew :app:connectedDebugAndroidTest   # emulator/device: Room DAOs and repositories, UI interaction tests, screenshots
```

GitHub Actions (`.github/workflows/ci.yml`) runs all four on every push (the last one on an API 30 emulator), publishes debug APKs to
the `debug-apk` and `debug-apk-arm64` branches, and publishes light and dark screenshots of the redesigned screens to the
`ui-screenshots` branch.

## Configuration you must provide (never committed)

| What | Where | Without it |
|---|---|---|
| Release signing key | `keystore.properties` (see `.example`) | release build is unsigned |
| AdMob app + banner IDs | `secrets.properties` (see `.example`) | release shows **no ads**; debug uses test IDs |
| Play Billing products | Play Console, see `docs/PLAY_CONSOLE_SETUP.md` | Pro screen says purchases are not set up |
| Google Drive backup | Google Cloud, see `docs/GOOGLE_DRIVE_SETUP.md` | Drive shown as "not set up" (local backup works) |

## Release checklist
1. `keystore.properties` and (if you want ads) `secrets.properties` in place; bump `versionCode`/`versionName` in `app/build.gradle.kts`.
2. `./gradlew :app:bundleRelease` and test the R8-minified build on a device (backup restore, scanning, reminders, billing, ads).
3. Play Console: Data safety form, ads declaration, privacy-policy URL, permission declarations (list in `docs/PLAY_CONSOLE_SETUP.md`).
4. Commit the Room schema JSON in `app/schemas/` after the first build; from the first release every schema change needs a migration.

## Privacy rules the code follows
No user-facing string is hard-coded; no personal data is logged (`AppLog` takes counts and ids only); contacts never leave the device
except through exports, shares and backups the user starts; OCR text is never stored; ads receive no app data and never appear on a
screen showing an individual's details.
