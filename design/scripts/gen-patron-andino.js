/**
 * Generador del patrón andino de fondo (estilo textil: rombos anidados en damero
 * verde/terracota). Produce un VectorDrawable de Android tileable visualmente.
 *
 * Por qué un script: el patrón son ~130 rombos con coordenadas calculadas; a mano
 * sería propenso a errores. Acá se computa la teselación y se escribe el XML.
 *
 * Uso:  node design/scripts/gen-patron-andino.js
 * Salida: app/src/main/res/drawable/bg_patron_andino.xml
 *
 * Paleta MinkIA: verde_hoja #69802D, terracota #BD4C18, verde_bosque #1B4228.
 */
const fs = require('fs');
const path = require('path');

// Viewport con proporción cercana a un teléfono (9:19) para minimizar distorsión.
const W = 360;
const H = 760;
const cell = 72;          // ancho de celda del rombo grande
const half = cell / 2;    // semidiagonal del rombo grande
const inner = half * 0.46; // semidiagonal del rombo interno (acento)
const stepY = cell / 2;   // paso vertical de la teselación de rombos

const n = (v) => Number(v.toFixed(1));
// Subpath de un rombo (cuadrado rotado 45°) centrado en cx,cy con semidiagonal r.
const rombo = (cx, cy, r) =>
  `M${n(cx)} ${n(cy - r)} L${n(cx + r)} ${n(cy)} L${n(cx)} ${n(cy + r)} L${n(cx - r)} ${n(cy)} Z`;

const grandesA = []; // rombos grandes del damero par (relleno verde)
const grandesB = []; // rombos grandes del damero impar (relleno terracota)
const internosA = []; // rombo interno (acento terracota dentro de celdas verdes)
const internosB = []; // rombo interno (acento verde dentro de celdas terracota)
const contorno = []; // contorno de todos los rombos grandes

let fila = 0;
for (let cy = -half; cy <= H + cell; cy += stepY, fila++) {
  const offsetX = (fila % 2) * half; // filas impares corridas media celda: teselan
  let col = 0;
  for (let cx = -cell; cx <= W + cell; cx += cell, col++) {
    const x = cx + offsetX;
    const grande = rombo(x, cy, half);
    const chico = rombo(x, cy, inner);
    contorno.push(grande);
    if ((fila + col) % 2 === 0) {
      grandesA.push(grande);
      internosA.push(chico);
    } else {
      grandesB.push(grande);
      internosB.push(chico);
    }
  }
}

const xml = `<?xml version="1.0" encoding="utf-8"?>
<!--
  Patrón andino de fondo (rombos anidados estilo textil).
  GENERADO por design/scripts/gen-patron-andino.js — NO editar a mano.
  Para ajustar densidad/colores: editá el script y re-ejecutalo.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="360dp"
    android:height="760dp"
    android:viewportWidth="${W}"
    android:viewportHeight="${H}">

    <!-- Rombos grandes: damero verde / terracota, muy suaves -->
    <path android:fillColor="#69802D" android:fillAlpha="0.15" android:pathData="${grandesA.join(' ')}" />
    <path android:fillColor="#BD4C18" android:fillAlpha="0.13" android:pathData="${grandesB.join(' ')}" />

    <!-- Rombos internos: acento del color opuesto (efecto anidado/tejido) -->
    <path android:fillColor="#BD4C18" android:fillAlpha="0.22" android:pathData="${internosA.join(' ')}" />
    <path android:fillColor="#69802D" android:fillAlpha="0.24" android:pathData="${internosB.join(' ')}" />

    <!-- Contorno de los rombos (greca tenue) -->
    <path android:strokeColor="#1B4228" android:strokeAlpha="0.16" android:strokeWidth="1.1"
        android:fillColor="#00000000" android:pathData="${contorno.join(' ')}" />
</vector>
`;

const out = path.resolve(__dirname, '../../app/src/main/res/drawable/bg_patron_andino.xml');
fs.writeFileSync(out, xml, 'utf8');
console.log(`Escrito: ${out}\nRombos: ${contorno.length} (A=${grandesA.length}, B=${grandesB.length})`);

// Preview en HTML/SVG (mismo patrón + base crema + resplandor) para revisión visual.
const svg = `<!doctype html><meta charset="utf-8">
<style>html,body{margin:0}svg{display:block}</style>
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <defs><radialGradient id="glow" cx="50%" cy="42%" r="55%">
    <stop offset="0%" stop-color="#F4EFE4" stop-opacity="0.95"/>
    <stop offset="50%" stop-color="#F4EFE4" stop-opacity="0.50"/>
    <stop offset="100%" stop-color="#F4EFE4" stop-opacity="0"/>
  </radialGradient></defs>
  <rect width="${W}" height="${H}" fill="#F4EFE4"/>
  <path d="${grandesA.join(' ')}" fill="#69802D" fill-opacity="0.15"/>
  <path d="${grandesB.join(' ')}" fill="#BD4C18" fill-opacity="0.13"/>
  <path d="${internosA.join(' ')}" fill="#BD4C18" fill-opacity="0.22"/>
  <path d="${internosB.join(' ')}" fill="#69802D" fill-opacity="0.24"/>
  <path d="${contorno.join(' ')}" fill="none" stroke="#1B4228" stroke-opacity="0.16" stroke-width="1.1"/>
  <rect width="${W}" height="${H}" fill="url(#glow)"/>
</svg>`;
const prev = path.resolve(__dirname, '../_preview-patron.html');
fs.writeFileSync(prev, svg, 'utf8');
console.log(`Preview: ${prev}`);

// Lámina comparativa de las 4 composiciones (armónico, no repetitivo).
const patron = `
  <rect width="${W}" height="${H}" fill="#F4EFE4"/>
  <path d="${grandesA.join(' ')}" fill="#69802D" fill-opacity="0.15"/>
  <path d="${grandesB.join(' ')}" fill="#BD4C18" fill-opacity="0.13"/>
  <path d="${internosA.join(' ')}" fill="#BD4C18" fill-opacity="0.22"/>
  <path d="${internosB.join(' ')}" fill="#69802D" fill-opacity="0.24"/>
  <path d="${contorno.join(' ')}" fill="none" stroke="#1B4228" stroke-opacity="0.16" stroke-width="1.1"/>`;
const lienzo = (titulo, overlayDefs, overlayRect) => `
  <figure>
    <svg xmlns="http://www.w3.org/2000/svg" width="180" height="${Math.round(180 * H / W)}" viewBox="0 0 ${W} ${H}">
      <defs>${overlayDefs || ''}</defs>
      ${patron}
      ${overlayRect || ''}
    </svg>
    <figcaption>${titulo}</figcaption>
  </figure>`;
const sheet = `<!doctype html><meta charset="utf-8">
<style>
  body{margin:0;background:#3a3a3a;font-family:'Segoe UI',Arial,sans-serif}
  .row{display:flex;gap:18px;padding:20px;justify-content:center}
  figure{margin:0;text-align:center}
  svg{display:block;border-radius:14px;box-shadow:0 6px 20px rgba(0,0,0,.35)}
  figcaption{color:#eee;font-size:13px;margin-top:8px}
</style>
<div class="row">
  ${lienzo('Splash / Onboarding<br>(completo + glow)',
    '<radialGradient id="c" cx="50%" cy="42%" r="55%"><stop offset="0%" stop-color="#F4EFE4" stop-opacity=".95"/><stop offset="50%" stop-color="#F4EFE4" stop-opacity=".5"/><stop offset="100%" stop-color="#F4EFE4" stop-opacity="0"/></radialGradient>',
    `<rect width="${W}" height="${H}" fill="url(#c)"/>`)}
  ${lienzo('Login<br>(banda superior)',
    '<linearGradient id="s" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#F4EFE4" stop-opacity=".06"/><stop offset="50%" stop-color="#F4EFE4" stop-opacity=".65"/><stop offset="100%" stop-color="#F4EFE4" stop-opacity="1"/></linearGradient>',
    `<rect width="${W}" height="${H}" fill="url(#s)"/>`)}
  ${lienzo('Registro / Recuperar<br>(tenue)', '',
    `<rect width="${W}" height="${H}" fill="#F4EFE4" fill-opacity="0.6"/>`)}
  ${lienzo('Permisos<br>(banda inferior)',
    '<linearGradient id="i" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#F4EFE4" stop-opacity="1"/><stop offset="50%" stop-color="#F4EFE4" stop-opacity=".65"/><stop offset="100%" stop-color="#F4EFE4" stop-opacity=".06"/></linearGradient>',
    `<rect width="${W}" height="${H}" fill="url(#i)"/>`)}
</div>`;
const sheetPath = path.resolve(__dirname, '../_preview-variantes.html');
fs.writeFileSync(sheetPath, sheet, 'utf8');
console.log(`Lámina: ${sheetPath}`);
