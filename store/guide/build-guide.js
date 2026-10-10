// Builds store/guide/VCardly-Play-Store-Guide.pdf from guide.html, filling in the listing texts from store/LISTING.md so
// the guide and the listing never disagree. Run from the repo root: NODE_PATH=$(npm root -g) node store/guide/build-guide.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const dir = __dirname;
const listing = fs.readFileSync(path.join(dir, '..', 'LISTING.md'), 'utf8');
const section = (title) => listing.split(title)[1].split('\n## ')[0].trim().split('\n\n(')[0].trim();
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const values = {
  APP_NAME: section('## App name (max 30)'),
  SHORT_DESC: section('## Short description (max 80)'),
  FULL_DESC: section('## Full description (max 4000)'),
  WHATS_NEW: section("## What's new (first release, max 500)").replace(/\n/g, ' '),
  DATE: new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }),
};
let html = fs.readFileSync(path.join(dir, 'guide.html'), 'utf8');
for (const [k, v] of Object.entries(values)) html = html.split('{{' + k + '}}').join(esc(v));
const filled = path.join(dir, '.guide-filled.html');
fs.writeFileSync(filled, html);

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('file://' + filled);
  await page.waitForLoadState('load');
  await page.evaluate(() => document.fonts.ready);
  await page.pdf({
    path: path.join(dir, 'VCardly-Play-Store-Guide.pdf'),
    format: 'A4',
    printBackground: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: '<div style="width:100%;font-size:8px;color:#8A93AD;padding:0 16mm;display:flex;justify-content:space-between"><span>VCardly · Google Play guide</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
    margin: { top: '16mm', bottom: '18mm', left: '16mm', right: '16mm' },
  });
  await browser.close();
  fs.unlinkSync(filled);
  console.log('written', Object.fromEntries(Object.entries(values).map(([k, v]) => [k, v.length])));
})();
