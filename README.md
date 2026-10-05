# VCardly

Offline-first business-card manager for Android: scan or type contacts, organise them with categories and tags, set follow-up
reminders, share a digital card with a QR code, and keep everything on your own device.

Kotlin, Jetpack Compose (Material 3), MVVM / clean layering, Hilt, Room, DataStore, Coroutines/Flow, CameraX, ML Kit text
recognition, AndroidX Biometric, WorkManager + AlarmManager, Play Billing, AdMob. Package `com.yasin.vcardly`, minSdk 26, target 35.
English only (right-to-left layouts are supported by the manifest and Compose, but no translations ship).

See **[PROJECT_STATUS.md](PROJECT_STATUS.md)** for what is built, the assumptions made and what still needs your configuration.

## Build and test

Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir=...`, or `ANDROID_HOME`).

```
./gradlew :app:assembleDebug          # debug APK (uses Google's test ad IDs)
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lintDebug              # Android lint
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (emulator/device): Room DAOs and repositories
```

GitHub Actions (`.github/workflows/ci.yml`) runs the first three on every push and uploads the debug APK and reports.

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
