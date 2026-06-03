const puppeteer = require('puppeteer-core');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars'] });
  const pg = await b.newPage();
  await pg.setViewport({ width: 1200, height: 900, deviceScaleFactor: 2 });
  await pg.goto('file:///C:/dev/MinkIA/design/html/marca-minkia.html', { waitUntil: 'networkidle0', timeout: 60000 });
  try { await pg.evaluate(() => document.fonts && document.fonts.ready); } catch (e) {}
  await new Promise(r => setTimeout(r, 1400));
  const el = await pg.$('.board');
  await el.screenshot({ path: 'C:/dev/MinkIA/design/png/marca/marca-minkia.png' });
  await b.close();
  console.log('OK -> marca-minkia.png');
})().catch(e => { console.error('FATAL', e); process.exit(1); });
