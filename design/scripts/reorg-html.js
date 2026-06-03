const fs = require('fs');
const { CITIZEN, ADMIN, pad } = require('./flowmap');

const FILES = [
  { path: 'C:/dev/MinkIA/minkia-mockups.html', adminTag: '<span class="tag admin">Admin</span>', adminRule: ' style="background:linear-gradient(90deg,var(--orange-soft),transparent)"' },
  { path: 'C:/dev/MinkIA/minkia-wireframes.html', adminTag: '<span class="tag">Admin</span>', adminRule: '' },
];

const reNum = (blockHtml, seq) => blockHtml.replace(/(<span class="num">)\d+(<\/span>)/, `$1${seq}$2`);

for (const f of FILES) {
  let html = fs.readFileSync(f.path, 'utf8');
  const hStart = html.indexOf('<div class="flow-label">');
  const fStart = html.indexOf('<div class="helper">');
  if (hStart < 0 || fStart < 0) { console.error('ANCHORS NOT FOUND', f.path); continue; }
  const header = html.slice(0, hStart);
  const footer = html.slice(fStart);
  const middle = html.slice(hStart, fStart);

  const re = /<div class="frame-wrap">[\s\S]*?<div class="frame-cap[^"]*"><span class="num">\d+<\/span>([^<]*)<\/div>\s*<\/div>/g;
  const byTitle = {}; let m, count = 0;
  while ((m = re.exec(middle))) { byTitle[m[1].trim()] = m[0]; count++; }

  let cit = '', adm = '', missing = [];
  CITIZEN.forEach(([t, slug], i) => { const b = byTitle[t]; if (!b) { missing.push('C:' + t); return; } cit += `\n  <!-- C${pad(i + 1)} · ${slug} -->\n  ${reNum(b, i + 1)}\n`; });
  ADMIN.forEach(([t, slug], i) => { const b = byTitle[t]; if (!b) { missing.push('A:' + t); return; } adm += `\n  <!-- A${pad(i + 1)} · ${slug} -->\n  ${reNum(b, i + 1)}\n`; });

  const section =
    `<div class="flow-label">${'<span class="tag">Ciudadano</span>'}<h2>Flujo del vecino · Ciudadano</h2><div class="rule"></div></div>\n` +
    `<div class="gallery">\n${cit}\n</div>\n\n` +
    `<div class="flow-label">${f.adminTag}<h2>Flujo del administrador</h2><div class="rule"${f.adminRule}></div></div>\n` +
    `<div class="gallery">\n${adm}\n</div>\n\n`;

  fs.writeFileSync(f.path, header + section + footer, 'utf8');
  console.log(`${f.path} -> extraídos:${count}  ciudadano:${CITIZEN.length}  admin:${ADMIN.length}  faltan:[${missing.join(', ')}]`);
}
