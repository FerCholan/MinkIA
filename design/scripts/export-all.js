const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');
const { lookupByTitle } = require('./flowmap');

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const JOBS = [
  { html: 'file:///C:/dev/MinkIA/design/html/minkia-mockups.html', out: 'C:\\dev\\MinkIA\\design\\png\\mockups', dev: '.device', pdf: '00-mockups-completo.pdf' },
  { html: 'file:///C:/dev/MinkIA/design/html/minkia-wireframes.html', out: 'C:\\dev\\MinkIA\\design\\png\\wireframes', dev: '.wdevice', pdf: '00-wireframes-completo.pdf' },
];

(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars'] });
  for (const j of JOBS) {
    fs.mkdirSync(path.join(j.out, 'ciudadano'), { recursive: true });
    fs.mkdirSync(path.join(j.out, 'admin'), { recursive: true });
    const page = await browser.newPage();
    await page.setViewport({ width: 1440, height: 1000, deviceScaleFactor: 2 });
    await page.goto(j.html, { waitUntil: 'networkidle0', timeout: 60000 });
    try { await page.evaluate(() => document.fonts && document.fonts.ready); } catch (e) {}
    await new Promise(r => setTimeout(r, 1500));
    const frames = await page.$$('.frame-wrap');
    let ok = 0;
    for (const fr of frames) {
      const cap = await fr.$eval('.frame-cap', el => el.textContent.replace(/\s+/g, ' ').trim());
      const title = cap.replace(/^\s*\d+\s*/, '').trim();
      const info = lookupByTitle(title);
      if (!info) { console.log('  NO MAP:', JSON.stringify(title)); continue; }
      const dev = await fr.$(j.dev);
      await dev.screenshot({ path: path.join(j.out, info.role, info.name + '.png') });
      ok++;
    }
    await page.pdf({ path: path.join(j.out, j.pdf), printBackground: true, width: '1440px', height: '2300px' }).catch(e => console.log('pdf err', e.message));
    await page.close();
    console.log(`${path.basename(j.out)}: ${ok} PNG -> ciudadano/ + admin/  (+ ${j.pdf})`);
  }
  await browser.close();
  console.log('DONE');
})().catch(e => { console.error('FATAL', e); process.exit(1); });
