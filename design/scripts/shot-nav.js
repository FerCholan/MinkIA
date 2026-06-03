const puppeteer = require('puppeteer-core');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox', '--disable-gpu', '--hide-scrollbars'] });
  const pg = await b.newPage();
  await pg.setViewport({ width: 1500, height: 1200, deviceScaleFactor: 2 });
  await pg.goto('file:///C:/dev/MinkIA/design/html/diagrama-navegabilidad.html', { waitUntil: 'networkidle0', timeout: 60000 });
  try { await pg.evaluate(() => document.fonts && document.fonts.ready); } catch (e) {}
  await new Promise(r => setTimeout(r, 1800));
  for (const id of ['nav-ciudadano', 'nav-admin']) {
    const el = await pg.$('#' + id);
    await el.screenshot({ path: 'C:/dev/MinkIA/design/png/diagramas/' + id + '.png' });
    console.log('OK -> ' + id + '.png');
  }
  await b.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
