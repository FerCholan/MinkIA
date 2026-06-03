const puppeteer = require('puppeteer-core');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const FIGS = ['fig-principal', 'fig-isotipo', 'fig-versiones', 'fig-construccion'];
(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars'] });
  const pg = await b.newPage();
  await pg.setViewport({ width: 1200, height: 1400, deviceScaleFactor: 2 });
  await pg.goto('file:///C:/dev/MinkIA/design/html/figuras-marca.html', { waitUntil: 'networkidle0', timeout: 60000 });
  try { await pg.evaluate(() => document.fonts && document.fonts.ready); } catch (e) {}
  await new Promise(r => setTimeout(r, 1400));
  for (const id of FIGS) {
    const el = await pg.$('#' + id);
    await el.screenshot({ path: 'C:/dev/MinkIA/design/png/marca/logo-' + id.replace('fig-', '') + '.png' });
    console.log('OK -> logo-' + id.replace('fig-', '') + '.png');
  }
  await b.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
