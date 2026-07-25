/**
 * Genera el logotipo MinkIA como vector a partir de Poppins SemiBold y emite:
 *   - drawable/avd_logo_minkia.xml      animated-vector: las letras se dibujan solas (splash)
 *   - drawable/logo_minkia_vector.xml   vector estatico con el estado final (login)
 *
 * El wordmark del manual de marca (design/html/marca-minkia.html) es Poppins SemiBold
 * con terminaciones rectas, pero el logo que usa la app (drawable/logo_minkia.png) las
 * tiene redondeadas y va mas espaciado. Para que el splash animado no rompa con el resto
 * de la app, el interletrado y el redondeo se CALIBRAN contra proporciones medidas sobre
 * ese PNG (ver design/scripts/medir-logo-png.mjs) en vez de fijarlos a ojo.
 *
 * Uso: node design/scripts/gen-logo-avd.mjs
 */
import fs from 'node:fs'
import opentype from 'opentype.js'

const RAIZ = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const FONT = `${RAIZ}app/src/main/res/font/poppins_semibold.ttf`
const DIR_DRAWABLE = `${RAIZ}app/src/main/res/drawable`

const BOSQUE = '#1B4228'
const NARANJA = '#BD4C18'
const HOJA = '#69802D'
const CREMA = '#F4EFE4'

/** Proporciones medidas sobre drawable/logo_minkia.png, todas relativas al alto de mayuscula. */
const OBJETIVO = {
  anchoTotal: 4.7456,
  grosorTallo: 0.2191,
  hojaAlto: 0.5972,
  hojaCentroX: 1.4982, // desde el borde izquierdo de la tinta
  hojaCentroY: 0.9046, // sobre la linea de base
}

const EM = 100
const VP_W = 300
const PAD = 2
const ROT_HOJA = 34 // el manual la inclina 16 grados; el PNG la lleva mas diagonal

// --- utilidades de geometria ---------------------------------------------------
const PUNTOS = [['x', 'y'], ['x1', 'y1'], ['x2', 'y2']]

const transformar = (cmds, m) =>
  cmds.map((c) => {
    const out = { ...c }
    for (const [cx, cy] of PUNTOS) {
      if (out[cx] === undefined) continue
      const x = out[cx]
      const y = out[cy]
      out[cx] = m.a * x + m.c * y + m.e
      out[cy] = m.b * x + m.d * y + m.f
    }
    return out
  })

const trasladar = (cmds, dx, dy) => transformar(cmds, { a: 1, b: 0, c: 0, d: 1, e: dx, f: dy })
const escalar = (cmds, s) => transformar(cmds, { a: s, b: 0, c: 0, d: s, e: 0, f: 0 })

const rotar = (cmds, deg, cx, cy) => {
  const r = (deg * Math.PI) / 180
  const a = Math.cos(r)
  const b = Math.sin(r)
  return transformar(cmds, { a, b, c: -b, d: a, e: cx - a * cx + b * cy, f: cy - b * cx - a * cy })
}

const bbox = (cmds) => {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity
  for (const c of cmds) {
    for (const [cx, cy] of PUNTOS) {
      if (c[cx] === undefined) continue
      x0 = Math.min(x0, c[cx]); x1 = Math.max(x1, c[cx])
      y0 = Math.min(y0, c[cy]); y1 = Math.max(y1, c[cy])
    }
  }
  return { x0, y0, x1, y1, w: x1 - x0, h: y1 - y0 }
}

const unir = (bs) => ({
  x0: Math.min(...bs.map((b) => b.x0)), y0: Math.min(...bs.map((b) => b.y0)),
  x1: Math.max(...bs.map((b) => b.x1)), y1: Math.max(...bs.map((b) => b.y1)),
})

const n = (v) => Number(v.toFixed(2))

const aPathData = (cmds) =>
  cmds.map((c) => {
    if (c.type === 'M') return `M${n(c.x)},${n(c.y)}`
    if (c.type === 'L') return `L${n(c.x)},${n(c.y)}`
    if (c.type === 'C') return `C${n(c.x1)},${n(c.y1)} ${n(c.x2)},${n(c.y2)} ${n(c.x)},${n(c.y)}`
    if (c.type === 'Q') return `Q${n(c.x1)},${n(c.y1)} ${n(c.x)},${n(c.y)}`
    return 'Z'
  }).join('')

// --- glifos --------------------------------------------------------------------
const font = opentype.parse(fs.readFileSync(FONT).buffer)

const GLIFOS = [
  { ch: 'M', color: BOSQUE },
  { ch: 'ı', color: BOSQUE, esI: true }, // i sin punto: la hoja hace de punto
  { ch: 'n', color: BOSQUE },
  { ch: 'k', color: BOSQUE },
  { ch: 'I', color: NARANJA, esMayusculaI: true },
  { ch: 'A', color: NARANJA },
]

for (const g of GLIFOS) {
  if (!font.charToGlyph(g.ch).unicode) throw new Error(`Poppins no tiene el glifo ${g.ch}`)
}

/** Dispone las letras con un interletrado dado y devuelve sus paths + metricas. */
const componer = (interletrado) => {
  let cursor = 0
  const piezas = []
  for (const g of GLIFOS) {
    const glyph = font.charToGlyph(g.ch)
    const cmds = glyph.getPath(cursor, 0, EM).commands
    piezas.push({ ...g, cmds, bb: bbox(cmds) })
    cursor += (glyph.advanceWidth / font.unitsPerEm) * EM + interletrado
  }
  return piezas
}

// --- calibracion ---------------------------------------------------------------
// La "I" mayuscula da el alto de mayuscula y el grosor de tallo de referencia.
const base = componer(0)
const bbI = base.find((p) => p.esMayusculaI).bb

// grosor: (anchoI + trazo) / (altoI + trazo) = objetivo  ->  despejar trazo
const TRAZO = (OBJETIVO.grosorTallo * bbI.h - bbI.w) / (1 - OBJETIVO.grosorTallo)
const ALTO_MAYUSCULA = bbI.h + TRAZO

// ancho: el union bbox crece linealmente con el interletrado (5 huecos entre 6 letras)
const anchoObjetivo = OBJETIVO.anchoTotal * ALTO_MAYUSCULA - TRAZO
const anchoCon0 = unir(base.map((p) => p.bb)).x1 - unir(base.map((p) => p.bb)).x0
const INTERLETRADO = (anchoObjetivo - anchoCon0) / (GLIFOS.length - 1)

const piezas = componer(INTERLETRADO)
piezas.forEach((p, i) => { p.nombre = `l${i}` })
const bbLetras = unir(piezas.map((p) => p.bb))
const tintaIzquierda = bbLetras.x0 - TRAZO / 2

// --- hoja (punto de la i) ------------------------------------------------------
// Formas tomadas del simbolo #leaf del manual de marca (viewBox 0 0 100 100).
const CUERPO_HOJA = [
  { type: 'M', x: 50, y: 6 },
  { type: 'C', x1: 24, y1: 26, x2: 20, y2: 64, x: 50, y: 96 },
  { type: 'C', x1: 80, y1: 64, x2: 76, y2: 26, x: 50, y: 6 },
  { type: 'Z' },
]
// El nervio va mas corto y fino que en el manual: a tamano de splash, el original
// tapaba la hoja en vez de insinuarla.
const NERVIO_HOJA = [
  { type: 'M', x: 50, y: 84 },
  { type: 'C', x1: 50, y1: 62, x2: 50, y2: 40, x: 50, y: 24 },
]
const GROSOR_NERVIO_BASE = 3.5

let cuerpo = rotar(CUERPO_HOJA, ROT_HOJA, 50, 50)
let nervio = rotar(NERVIO_HOJA, ROT_HOJA, 50, 50)

// Escala para que el alto de la hoja ya rotada respete la proporcion medida.
const sHoja = (OBJETIVO.hojaAlto * ALTO_MAYUSCULA) / bbox(cuerpo).h
cuerpo = escalar(cuerpo, sHoja)
nervio = escalar(nervio, sHoja)

// Centra la hoja donde la tiene el PNG (la linea de base esta en y = 0).
const bbCuerpo = bbox(cuerpo)
const dx = tintaIzquierda + OBJETIVO.hojaCentroX * ALTO_MAYUSCULA - (bbCuerpo.x0 + bbCuerpo.x1) / 2
const dy = -OBJETIVO.hojaCentroY * ALTO_MAYUSCULA - (bbCuerpo.y0 + bbCuerpo.y1) / 2
cuerpo = trasladar(cuerpo, dx, dy)
nervio = trasladar(nervio, dx, dy)

const GROSOR_NERVIO = GROSOR_NERVIO_BASE * sHoja

// --- normalizacion al viewport -------------------------------------------------
const total = unir([bbLetras, bbox(cuerpo)])
const k = VP_W / (total.x1 - total.x0 + TRAZO + PAD * 2)
const VP_H = Number(((total.y1 - total.y0 + TRAZO + PAD * 2) * k).toFixed(1))

const normalizar = (cmds) =>
  transformar(cmds, {
    a: k, b: 0, c: 0, d: k,
    e: (PAD + TRAZO / 2 - total.x0) * k,
    f: (PAD + TRAZO / 2 - total.y0) * k,
  })

for (const p of piezas) p.d = aPathData(normalizar(p.cmds))
const dCuerpo = aPathData(normalizar(cuerpo))
const dNervio = aPathData(normalizar(nervio))
const trazoVp = TRAZO * k
const nervioVp = GROSOR_NERVIO * k

// --- capas ---------------------------------------------------------------------
// Cada forma se dibuja dos veces: una linea fina que se traza sola (efecto pluma)
// y debajo la forma final, que aparece cuando el trazo ya la recorrio.
const FORMAS = [
  ...piezas.map((p) => ({ nombre: p.nombre, d: p.d, color: p.color, grosor: trazoVp })),
  { nombre: 'hoja', d: dCuerpo, color: HOJA, grosor: trazoVp },
]

// Unico dial para acelerar o frenar todo el trazado: 1 = ritmo base, >1 mas lento.
// Al cambiarlo hay que copiar la duracion que imprime el script a SPLASH_MS.
const RITMO = 1.55

const PLUMA = 1.2
const ms = (base) => Math.round(base * RITMO)
const TRAZO_MS = ms(620)
const DESFASE_MS = ms(95)
const RELLENO_MS = ms(340)
const NERVIO_MS = ms(340)

const tiempos = FORMAS.map((_, i) => i * DESFASE_MS)
const inicioNervio = tiempos[tiempos.length - 1] + Math.round(TRAZO_MS * 0.55)
const DURACION = Math.max(
  ...tiempos.map((t) => t + Math.round(TRAZO_MS * 0.62) + RELLENO_MS),
  inicioNervio + NERVIO_MS,
)

// --- emision -------------------------------------------------------------------
const cabecera = (tag, extra) => `<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por design/scripts/gen-logo-avd.mjs desde poppins_semibold.ttf. No editar a mano. -->
<${tag} xmlns:android="http://schemas.android.com/apk/res/android"${extra}>`


fs.writeFileSync(
  `${DIR_DRAWABLE}/logo_minkia_vector.xml`,
  `${cabecera('vector', `
    android:width="${n(VP_W / 2)}dp"
    android:height="${n(VP_H / 2)}dp"
    android:viewportWidth="${VP_W}"
    android:viewportHeight="${VP_H}"`)}
${FORMAS.map((f) => `    <path
        android:pathData="${f.d}"
        android:fillColor="${f.color}"
        android:strokeColor="${f.color}"
        android:strokeWidth="${n(f.grosor)}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />`).join('\n')}
    <path
        android:pathData="${dNervio}"
        android:strokeColor="${CREMA}"
        android:strokeWidth="${n(nervioVp)}"
        android:strokeLineCap="round" />
</vector>
`,
)

const pathsAvd = FORMAS.map((f) => `            <path
                android:name="${f.nombre}"
                android:pathData="${f.d}"
                android:fillColor="${f.color}"
                android:fillAlpha="0"
                android:strokeColor="${f.color}"
                android:strokeWidth="${n(f.grosor)}"
                android:strokeAlpha="0"
                android:strokeLineCap="round"
                android:strokeLineJoin="round" />
            <path
                android:name="${f.nombre}_pluma"
                android:pathData="${f.d}"
                android:strokeColor="${f.color}"
                android:strokeWidth="${PLUMA}"
                android:strokeLineCap="round"
                android:strokeLineJoin="round"
                android:trimPathEnd="0" />`).join('\n')

const objetivos = FORMAS.flatMap((f, i) => {
  const t = tiempos[i]
  const aparece = t + Math.round(TRAZO_MS * 0.62)
  return [
    `    <target android:name="${f.nombre}_pluma">
        <aapt:attr name="android:animation">
            <set>
                <objectAnimator
                    android:propertyName="trimPathEnd"
                    android:valueFrom="0"
                    android:valueTo="1"
                    android:valueType="floatType"
                    android:startOffset="${t}"
                    android:duration="${TRAZO_MS}"
                    android:interpolator="@android:interpolator/fast_out_slow_in" />
                <objectAnimator
                    android:propertyName="strokeAlpha"
                    android:valueFrom="1"
                    android:valueTo="0"
                    android:valueType="floatType"
                    android:startOffset="${aparece + 80}"
                    android:duration="${RELLENO_MS}" />
            </set>
        </aapt:attr>
    </target>`,
    `    <target android:name="${f.nombre}">
        <aapt:attr name="android:animation">
            <set>
                <objectAnimator
                    android:propertyName="fillAlpha"
                    android:valueFrom="0"
                    android:valueTo="1"
                    android:valueType="floatType"
                    android:startOffset="${aparece}"
                    android:duration="${RELLENO_MS}"
                    android:interpolator="@android:interpolator/decelerate_quad" />
                <objectAnimator
                    android:propertyName="strokeAlpha"
                    android:valueFrom="0"
                    android:valueTo="1"
                    android:valueType="floatType"
                    android:startOffset="${aparece}"
                    android:duration="${RELLENO_MS}"
                    android:interpolator="@android:interpolator/decelerate_quad" />
            </set>
        </aapt:attr>
    </target>`,
  ]
}).join('\n')

fs.writeFileSync(
  `${DIR_DRAWABLE}/avd_logo_minkia.xml`,
  `${cabecera('animated-vector', `
    xmlns:aapt="http://schemas.android.com/aapt"`)}

    <aapt:attr name="android:drawable">
        <vector
            android:width="${n(VP_W / 2)}dp"
            android:height="${n(VP_H / 2)}dp"
            android:viewportWidth="${VP_W}"
            android:viewportHeight="${VP_H}">
${pathsAvd}
            <path
                android:name="nervio"
                android:pathData="${dNervio}"
                android:strokeColor="${CREMA}"
                android:strokeWidth="${n(nervioVp)}"
                android:strokeLineCap="round"
                android:trimPathEnd="0" />
        </vector>
    </aapt:attr>

${objetivos}
    <target android:name="nervio">
        <aapt:attr name="android:animation">
            <objectAnimator
                android:propertyName="trimPathEnd"
                android:valueFrom="0"
                android:valueTo="1"
                android:valueType="floatType"
                android:startOffset="${inicioNervio}"
                android:duration="${NERVIO_MS}"
                android:interpolator="@android:interpolator/fast_out_slow_in" />
        </aapt:attr>
    </target>
</animated-vector>
`,
)

// Vista previa estatica para comparar contra el PNG original.
fs.writeFileSync(
  new URL('./preview-logo.svg', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'),
  `<svg xmlns="http://www.w3.org/2000/svg" width="${VP_W}" height="${VP_H}" viewBox="0 0 ${VP_W} ${VP_H}">
<rect width="100%" height="100%" fill="#F4EFE4"/>
${FORMAS.map((f) => `<path d="${f.d}" fill="${f.color}" stroke="${f.color}" stroke-width="${n(f.grosor)}" stroke-linejoin="round" stroke-linecap="round"/>`).join('\n')}
<path d="${dNervio}" fill="none" stroke="${CREMA}" stroke-width="${n(nervioVp)}" stroke-linecap="round"/>
</svg>
`,
)

console.log(`trazo calibrado : ${n(TRAZO)} (em ${EM})`)
console.log(`interletrado    : ${n(INTERLETRADO)} (em ${EM})`)
console.log(`viewport        : ${VP_W} x ${VP_H}`)
console.log(`duracion        : ${DURACION} ms`)
console.log(`escrito -> ${DIR_DRAWABLE}/avd_logo_minkia.xml`)
console.log(`escrito -> ${DIR_DRAWABLE}/logo_minkia_vector.xml`)
