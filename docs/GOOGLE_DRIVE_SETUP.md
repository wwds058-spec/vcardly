# Google Drive backup: what is prepared and what you must configure

**Status: NOT configured.** The app contains the seam (`core/cloud/CloudBackupProvider`) and a placeholder that always reports
"not configured". There is no Drive code, no client ID and no fake upload. The Backup screen shows Google Drive as unavailable.

## What is already done
- Backups are a self-contained, versioned file (`vcardly-backup-v1`) with SHA-256 integrity checks and optional
  password encryption (PBKDF2 + AES-256-GCM, chunked). The same file can be stored anywhere.
- `CloudBackupProvider` defines upload / list / download for an *encrypted* backup file.

## What you must do (cannot be done from the repository)
1. **Google Cloud project**: create one at console.cloud.google.com and enable the **Google Drive API**.
2. **OAuth consent screen**: configure it (External or Internal). Request only the scope
   `https://www.googleapis.com/auth/drive.appdata` (a private app-data folder that the user cannot browse and other apps
   cannot read). It is a non-sensitive scope, so verification is usually simple. Do **not** request full Drive access.
3. **Android OAuth client**: create an OAuth client of type *Android* with package `com.yasin.vcardly` and the **SHA-1 of the
   signing key** for each build you will ship (debug key for testing, your release/Play App Signing key for production).
   No client secret is needed for an Android client, so nothing secret goes into git.
4. **Dependencies** (add to `gradle/libs.versions.toml` and `app/build.gradle.kts`): Google Identity Services
   (`com.google.android.gms:play-services-auth`) for the authorization request, and either the Drive REST client
   (`com.google.apis:google-api-services-drive` + `com.google.api-client:google-api-client-android`) or plain HTTPS calls to
   the Drive REST API with the returned access token.
5. **Implement `CloudBackupProvider`** (`DriveBackupProvider`): authorize with `AuthorizationClient` for the appdata scope,
   upload the encrypted file to `appDataFolder`, list/download for restore. Return `CloudResult.SignInRequired` when consent is
   needed. Replace `UnconfiguredDriveProvider` in `di/CloudModule.kt`, and flip the Backup screen card to use `isConfigured`.
6. **Manifest / network**: `INTERNET` is already declared (for ads and Play Billing only). Mention Drive in the Privacy screen
   and the Play Data safety form when you enable it, and keep the "your contacts are never uploaded" wording true by uploading
   only the password-encrypted backup.
7. **Enforce encryption for cloud**: refuse to upload unless a password was set; never upload the plain format.

## Test checklist once configured
- Fresh install: sign in, back up, uninstall, reinstall, restore from Drive (needs the password).
- Revoked consent, no network, quota exceeded and a corrupt remote file each show a clear message.
