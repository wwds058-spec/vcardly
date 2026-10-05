# VCardly

Offline-first business card manager for Android (Kotlin, Jetpack Compose, Material 3).
See [PROJECT_STATUS.md](PROJECT_STATUS.md) for progress, assumptions and what still needs external configuration.

## Build

Requires JDK 17+ and the Android SDK (create `local.properties` with `sdk.dir=...`).

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
