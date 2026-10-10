# VCardly — Play Store listing text

Ready to paste into Play Console (Grow → Store presence → Main store listing). Limits are Google's. Every claim matches what
the app does today; keep it that way when features change (Google rejects listings that promise more than the app does).

## App name (max 30)

VCardly: Business Card Scanner

(30 characters)

## Short description (max 80)

Scan visiting cards, keep contacts private on your phone, never miss follow-ups

(79 characters)

## Full description (max 4000)

VCardly turns the visiting cards you collect into organised contacts, and keeps them private on your phone.

SCAN A CARD IN SECONDS
• Point the camera at a card: VCardly finds its edges and straightens it, even when you hold it at an angle.
• Text is read on your phone and sorted into name, job title, company, phones, emails, website and address.
• Check and correct everything before you save. Scan the back of the card too.
• The original card photo is kept with the contact.

STAY ON TOP OF FOLLOW-UPS
• Plan calls, meetings and emails with reminders that arrive on time, even after a restart.
• See what is due today, upcoming and overdue at a glance.

ORGANISE YOUR NETWORK
• Categories, tags and favourites, with fast search and filters.
• Call, WhatsApp or email straight from a contact.
• Reports: new contacts per month, follow-ups by status, categories and tags.

SHARE YOUR OWN CARD
• Create your digital visiting card and share it as a QR code that phones save instantly.
• You choose exactly which details are shared.

PRIVATE BY DESIGN
• No account and no VCardly server: your contacts never leave your phone unless you share or export them.
• Optional App lock with fingerprint, face or PIN, and screen privacy.
• Encrypted backups that you control and keep wherever you choose.

IMPORT AND EXPORT
• Import contacts from .vcf files and export all contacts to one file.
• Export reports as PDF, CSV or Excel.

Accessible: works with TalkBack and the largest font sizes, in light and dark themes.

Free, with no ads and no limits: unlimited scans and every export included.

## What's new (first release, max 500)

First release of VCardly: scan visiting cards with automatic edge detection, keep contacts and follow-up reminders private on
your phone, share your own digital card by QR code, and back up with encryption.

## Category and details

- Category: Business (alternative: Productivity)
- Tags to pick in Play Console: Business card scanner, Contacts, CRM
- Contact email: [YOUR SUPPORT EMAIL] (shown publicly on the listing)
- Privacy policy URL: `https://wwds058-spec.github.io/vcardly/site/privacy.html` once published (see `docs/PLAY_CONSOLE_SETUP.md`)
- Contains ads: **No** (first release). In-app purchases: **No** (first release). Change both, and this description, in
  the update that turns on AdMob or VCardly Pro.

## Graphics you still need

| Asset | Size | Source |
|---|---|---|
| App icon | 512 × 512 PNG | **done**: `store/graphics/icon-512.png` (same V mark and gradient as the launcher icon) |
| Feature graphic | 1024 × 500 PNG/JPG | **done**: `store/graphics/feature-graphic.png` (source `feature-graphic.html`, re-render with a headless browser after edits) |
| Phone screenshots | 2–8, at most 2:1 | **done**: six 1080 × 1920 images in `store/graphics/screenshots/` (caption over a real app screen rendered by CI with fictional data; regenerate with `store/graphics/render-screenshots.js`) |

Suggested screenshot captions: "Scan a card in seconds" · "Straightened automatically" · "Everything about a contact in one
place" · "Never miss a follow-up" · "Share your card with a QR code" · "Private: no account, no server".
