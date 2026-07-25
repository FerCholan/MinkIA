/**
 * Mide drawable/logo_minkia.png y devuelve sus proporciones (interletrado, grosor de
 * tallo, tamano y posicion de la hoja) relativas al alto de mayuscula. Esos numeros son
 * los que gen-logo-avd.mjs usa como OBJETIVO para calibrar el logo vectorial, de modo
 * que el splash animado no desentone con el logo que ya usa la app.
 *
 * Uso: node design/scripts/medir-logo-png.mjs
 */
import fs from 'node:fs'
import { PNG } from 'pngjs'

const RAIZ = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const png = PNG.sync.read(fs.readFileSync(`${RAIZ}app/src/main/res/drawable/logo_minkia.png`))
const { width: W, height: H, data } = png

const px = (x, y) => {
  const i = (y * W + x) * 4
  return { r: data[i], g: data[i + 1], b: data[i + 2], a: data[i + 3] }
}

// Clasifica cada pixel: fondo, verde bosque (letras Mink), naranja (IA), hoja.
const clase = (p) => {
  if (p.a < 128) return 'bg'
  const { r, g, b } = p
  if (r > 225 && g > 225 && b > 225) return 'bg'
  if (r > g && r > b && r > 120) return 'naranja'
  if (g > r && g > 90 && r > 70) return 'hoja' // verde oliva claro
  if (g >= r) return 'bosque'
  return 'bg'
}

const mapa = []
for (let y = 0; y < H; y++) {
  const fila = []
  for (let x = 0; x < W; x++) fila.push(clase(px(x, y)))
  mapa.push(fila)
}

const bbox = (test) => {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity, n = 0
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (!test(mapa[y][x])) continue
    n++
    if (x < x0) x0 = x
    if (x > x1) x1 = x
    if (y < y0) y0 = y
    if (y > y1) y1 = y
  }
  return { x0, y0, x1, y1, w: x1 - x0 + 1, h: y1 - y0 + 1, n }
}

const tinta = bbox((c) => c !== 'bg')
const naranja = bbox((c) => c === 'naranja')
const hoja = bbox((c) => c === 'hoja')

// La "I" naranja es la primera columna continua de naranja: barra vertical limpia.
const colsNaranja = []
for (let x = naranja.x0; x <= naranja.x1; x++) {
  let hay = false
  for (let y = 0; y < H; y++) if (mapa[y][x] === 'naranja') { hay = true; break }
  colsNaranja.push(hay)
}
let iFin = 0
while (iFin < colsNaranja.length && colsNaranja[iFin]) iFin++
const anchoI = iFin
const xI0 = naranja.x0

// Altura de mayuscula = alto de la I naranja.
let iy0 = Infinity, iy1 = -Infinity
for (let y = 0; y < H; y++) for (let x = xI0; x < xI0 + anchoI; x++) {
  if (mapa[y][x] !== 'naranja') continue
  if (y < iy0) iy0 = y
  if (y > iy1) iy1 = y
}
const altoMayuscula = iy1 - iy0 + 1

const r = (v) => Number(v.toFixed(4))
console.log(JSON.stringify({
  imagen: { W, H },
  altoMayuscula,
  anchoTotalTinta: tinta.w,
  ratioAnchoTotal: r(tinta.w / altoMayuscula),
  grosorTallo: anchoI,
  ratioGrosorTallo: r(anchoI / altoMayuscula),
  hoja: {
    w: hoja.w, h: hoja.h,
    ratioAncho: r(hoja.w / altoMayuscula),
    ratioAlto: r(hoja.h / altoMayuscula),
    // centro de la hoja respecto al borde izquierdo de la tinta y a la linea de base
    cxRel: r((hoja.x0 + hoja.x1) / 2 - tinta.x0),
    cxRatio: r(((hoja.x0 + hoja.x1) / 2 - tinta.x0) / altoMayuscula),
    baseRatio: r((iy1 - (hoja.y0 + hoja.y1) / 2) / altoMayuscula),
  },
  lineaBase: iy1, topeMayuscula: iy0,
}, null, 2))
