const puppeteer = require('puppeteer-core');
const path = require('path');

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const HTML = 'file:///C:/dev/MinkIA/minkia-wireframes.html';
const OUT = 'C:\\dev\\MinkIA\\wireframes-png';

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: 'new',
    args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 1000, deviceScaleFactor: 2 });
  await page.goto(HTML, { waitUntil: 'networkidle0', timeout: 60000 });
  try { await page.evaluate(() => document.fonts && document.fonts.ready); } catch (e) {}
  await new Promise(r => setTimeout(r, 1500));

  const frames = await page.$$('.frame-wrap');
  let i = 0;
  for (const f of frames) {
    i++;
    let cap = await f.$eval('.frame-cap', el => el.textContent.replace(/\s+/g, ' ').trim());
    let num = (await f.$eval('.frame-cap .num', el => el.textContent.trim()).catch(() => String(i)));
    cap = cap.replace(num, '').trim();
    const device = await f.$('.wdevice');
    const slug = ('0' + num).slice(-2) + '-wf-' + cap.normalize('NFD').replace(/[̀-ͯ]/g, '')
      .replace(/[^A-Za-z0-9]+/g, '-').replace(/(^-|-$)/g, '').toLowerCase();
    await device.screenshot({ path: path.join(OUT, slug + '.png') });
    console.log('shot:', slug + '.png');
  }
  await page.pdf({ path: path.join(OUT, '00-minkia-wireframes.pdf'), printBackground: true, width: '1440px', height: '2300px' })
    .then(() => console.log('pdf: 00-minkia-wireframes.pdf')).catch(e => console.log('pdf err', e.message));
  await browser.close();
  console.log('TOTAL FRAMES:', i);
})().catch(e => { console.error('FATAL', e); process.exit(1); });
