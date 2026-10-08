const { chromium } = require('playwright');
// Renders the Play Store screenshots (1080 x 1920) from the CI screen renders in src/ (copied from the ui-screenshots branch,
// fictional sample data). Run from the repo root: NODE_PATH=$(npm root -g) node store/graphics/render-screenshots.js
const path = require('path');
const base = path.resolve(__dirname) + '/';
const shots = [
  ['01_scan', '07_ocr_review', 'Scan a card in seconds', 'Every detail read on your phone, ready to check'],
  ['02_contact', '04_contact_details', 'Everything in one place', 'Call, WhatsApp or email in one tap'],
  ['03_followups', '08_follow_ups', 'Never miss a follow-up', 'Reminders that arrive on time'],
  ['04_mycard', '09_my_card', 'Share your own card', 'One QR code, saved instantly'],
  ['05_reports', '10_reports', 'See your network grow', 'Contacts, categories and follow-ups at a glance'],
  ['06_home', '02_home', 'Private by design', 'No account, no server, no uploads'],
];
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
  for (const [out, img, title, sub] of shots) {
    const qs = new URLSearchParams({ img, title, sub }).toString();
    await p.goto('file://' + base + 'screenshot.html?' + qs);
    await p.waitForLoadState('load'); await p.waitForTimeout(300);
    await p.screenshot({ path: base + 'screenshots/' + out + '.png' });
  }
  await b.close();
})();
