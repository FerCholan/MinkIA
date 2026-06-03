# MinkIA — Diseño y entregables

Material de diseño del proyecto MinkIA (mockups, wireframes, identidad de marca y el documento Word). Separado del código Android, que vive fuera de `design/`.

## Estructura

```
design/
├── html/            Fuentes HTML (se renderizan a PNG)
│   ├── minkia-mockups.html       23 pantallas alta fidelidad
│   ├── minkia-wireframes.html    23 pantallas baja fidelidad (gris)
│   ├── marca-minkia.html         lámina de sistema de marca
│   ├── figuras-marca.html        figuras del logo para el Word
│   └── diagrama-navegabilidad.html  mapas de navegación (hub-and-spoke)
├── scripts/         Node (puppeteer) + Python (verificación)
├── png/
│   ├── marca/       logos y lámina de marca
│   ├── diagramas/   nav-ciudadano.png, nav-admin.png
│   ├── mockups/     ciudadano/ + admin/ (+ pdf)
│   └── wireframes/  ciudadano/ + admin/ (+ pdf)
├── docs/            Word final + presupuesto.md + identidad.md
└── _obsoletos/      versiones rechazadas (logo viejo, exploraciones, flujos lineales)
```

## Cómo regenerar (pipeline)

Requiere `node_modules` (docx + puppeteer-core) en `%TEMP%\minkia-shot\node_modules`. Se pasa por `NODE_PATH`. Usar `py` para Python.

```bash
cd C:/dev/MinkIA/design/scripts
NP="C:/Users/iamja/AppData/Local/Temp/minkia-shot/node_modules"

# 1. Si cambió la PALETA o los mockups/wireframes: re-exportar las 23 pantallas
NODE_PATH="$NP" node export-all.js

# 2. Si cambió la marca: figuras del logo
NODE_PATH="$NP" node shot-figuras.js     # -> png/marca/logo-*.png
NODE_PATH="$NP" node shot-marca.js       # -> png/marca/marca-minkia.png

# 3. Si cambiaron los diagramas de navegación
NODE_PATH="$NP" node shot-nav.js         # -> png/diagramas/nav-*.png

# 4. SIEMPRE al final (embebe los PNG anteriores): regenerar el Word
NODE_PATH="$NP" node generar-word-minkia.js   # -> docs/MinkIA_...docx

# 5. Verificar estructura (no hay Word/LibreOffice para render visual)
py verify-docx.py
py verify-figs.py
```

**Orden de dependencia:** paleta → re-exportar PNG → regenerar Word. El Word embebe los PNG; si se regenera antes de re-exportar, queda con imágenes viejas.

## Convenciones

- Prosa humana, **sin guion largo (—)**: usar coma, dos puntos o paréntesis.
- App en tuteo peruano. Rol genérico "administrador" (sin "Municipalidad").
- Paleta: verde bosque `#1B4228`, verde hoja `#69802D`, naranja terracota `#BD4C18`.
- Estilo Word: Times New Roman 12 / interlineado 1.5 / justificado / tablas APA.
