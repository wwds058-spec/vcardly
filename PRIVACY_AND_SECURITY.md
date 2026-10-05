# VCardly — privacy and security

What the app does with data, how it is protected, and the rules the code follows. Keep this file, the in-app Privacy screen
(`presentation/privacy`) and the Play Data-safety answers in sync: a change to what is stored or sent updates all three.

## Where data lives

| Data | Storage | Leaves the device? |
|---|---|---|
| Contacts, categories, tags, follow-ups | Room database `vcardly.db` in app-private storage | Only through an export, share or backup the user starts |
| Card photos | `filesDir/cards` (app-private, random UUID file names, written atomically: temp file then rename) | Same as above |
| Scan work in progress | `cacheDir/scan`, deleted when the scan ends | No |
| Restore staging | `cacheDir`, deleted after restore | No |
| Settings, My card | DataStore `vcardly_preferences` | My card only when the user shares it |
| Recognised card text (OCR) | Memory only; shown for review, never stored or logged | No |

Android auto-backup is off (`allowBackup="false"` plus `data_extraction_rules.xml`), so the system never uploads app data.
There is no account and no VCardly server.

## Network

`INTERNET` is used only by Google Mobile Ads (free plan) and Google Play Billing. No contact data is sent: ad requests carry no app
data, and ads appear only on Home, never next to a person's details. Google Drive backup is prepared behind `CloudBackupProvider` but
**not configured** (`UnconfiguredDriveProvider` makes no network calls and the UI says "Not set up in this build").

## Permissions and why

| Permission | Why | When it is asked |
|---|---|---|
| CAMERA | Photograph a visiting card | When the user opens the scanner or chooses "Take photo" |
| POST_NOTIFICATIONS (Android 13+) | Follow-up reminders | When the user turns a reminder on |
| SCHEDULE_EXACT_ALARM | Reminders on time | Never prompted; Settings explains and links to the system page |
| RECEIVE_BOOT_COMPLETED | Re-arm reminders after a restart | Install time (normal permission) |
| USE_BIOMETRIC | App lock | Install time (normal permission) |
| INTERNET, ACCESS_NETWORK_STATE | Ads and purchases only | Install time (normal permission) |

No storage permission: imports, exports and backups use the system file picker (Storage Access Framework).

## Protections

- **App lock**: AndroidX BiometricPrompt (fingerprint/face or the device PIN). Turning it on or off requires authenticating first.
  Auto-lock uses a monotonic clock, so changing the date cannot bypass it. If the device loses its screen lock, app lock switches
  itself off and tells the user (otherwise they would be locked out of their own data). App lock gates the UI; data at rest is protected
  by the app sandbox and device encryption, not by a second layer.
- **Screen privacy** (on by default): `FLAG_SECURE` hides content in Recents and blocks screenshots and screen recording.
- **Backups**: optional password encryption (PBKDF2-HMAC-SHA256 with 310,000 iterations, AES-256-GCM in authenticated 64 KiB
  chunks). Restores are staged and fully verified (format version, SHA-256 per entry, size limits, zip-slip protection) before the
  database is touched. Passwords are never stored and are wiped from memory after use.
- **Sharing**: only the fields the user selects go into a QR code or vCard file; private notes are never offered. Shared files go
  through `FileProvider` with a one-off read grant; the camera gets a one-off write grant to a single cache file.
- **Clipboard**: copied phone numbers and emails are marked sensitive on Android 13+ so the clipboard preview hides them.
- **Exports**: CSV cells are protected against formula injection; XLSX uses inline strings only.
- **Components**: nothing is exported except the launcher activity; cleartext traffic is disabled.

## Logging rules

Never log names, phone numbers, emails, addresses, notes, card images, file paths, OCR text, tokens or passwords. `AppLog` only
accepts counts and ids and only writes in debug builds.

## Known limits

- Purchases are verified on the device only (no server). A modified or rooted device could fake Pro.
- A lost backup password cannot be recovered.
- Rooted devices, `adb` access and a compromised OS are out of scope.
