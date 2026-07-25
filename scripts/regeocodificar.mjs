// Re-geocodifica la `direccion` de reportes viejos en Firestore a partir de sus
// coordenadas (backfill). Se autentica como admin vía la API REST de Firebase
// (Identity Toolkit) y parchea cada doc con updateMask=direccion. El geocoding
// usa la API de Google (Geocoding). NO borra ni toca otros campos.
//
// Requisitos:
//   - El usuario admin debe tener rol:'admin' en usuarios/{uid} (si no, las reglas
//     de Firestore rechazan el update).
//   - El Geocoding API debe estar habilitado en el proyecto Google Cloud, con una
//     key que funcione server-side (la de Maps del manifest está restringida a
//     Android y NO sirve acá).
//
// Uso (PowerShell):
//   $env:ADMIN_PASSWORD="lacontraseña"; $env:GEOCODING_KEY="AIza..."; node scripts/regeocodificar.mjs
//   (opcional)  $env:DRY_RUN="1"     -> solo muestra qué haría, no escribe
//   (opcional)  $env:REGEOCODE_ALL="1" -> re-geocodifica TODOS, no solo los genéricos
//   (opcional)  $env:ADMIN_EMAIL="otro@correo"  -> por defecto admin@minkia.pe

const PROJECT_ID = process.env.PROJECT_ID || "minkia-4a73d";
// API key de Firebase (del google-services.json). Sirve para el login REST.
const FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || "AIzaSyCAxk5q1rL1e9itvmS-hktEW-tfm7l93UI";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || "admin@minkia.pe";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;
const GEOCODING_KEY = process.env.GEOCODING_KEY;
const DRY_RUN = process.env.DRY_RUN === "1";
const REGEOCODE_ALL = process.env.REGEOCODE_ALL === "1";

const DOCS_URL = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/reportes`;

// Direcciones que consideramos "genéricas": vale la pena re-geocodificarlas.
const GENERICAS = ["", "chimbote", "chimbote, áncash", "chimbote, ancash"];

function esGenerica(dir) {
  return GENERICAS.includes((dir || "").trim().toLowerCase());
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login() {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD, returnSecureToken: true }),
    }
  );
  const data = await res.json();
  if (!res.ok) throw new Error(`Login falló: ${data.error?.message || res.status}`);
  return data.idToken;
}

// Lee TODOS los reportes (paginado). Devuelve [{id, direccion, lat, lng}].
async function listarReportes(token) {
  const out = [];
  let pageToken = "";
  do {
    const url = `${DOCS_URL}?pageSize=300${pageToken ? `&pageToken=${pageToken}` : ""}`;
    const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
    const data = await res.json();
    if (!res.ok) throw new Error(`Listar falló: ${data.error?.message || res.status}`);
    for (const doc of data.documents || []) {
      const id = doc.name.split("/").pop();
      const f = doc.fields || {};
      out.push({
        id,
        direccion: f.direccion?.stringValue ?? "",
        lat: numero(f.latitud),
        lng: numero(f.longitud),
      });
    }
    pageToken = data.nextPageToken || "";
  } while (pageToken);
  return out;
}

// Firestore tipa los números como doubleValue o integerValue: soportamos ambos.
function numero(campo) {
  if (!campo) return null;
  if (campo.doubleValue !== undefined) return Number(campo.doubleValue);
  if (campo.integerValue !== undefined) return Number(campo.integerValue);
  return null;
}

async function geocodificar(lat, lng) {
  const url = `https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lng}&language=es&key=${GEOCODING_KEY}`;
  const res = await fetch(url);
  const data = await res.json();
  if (data.status !== "OK") {
    throw new Error(`Geocoding ${data.status}: ${data.error_message || "sin resultados"}`);
  }
  return data.results[0]?.formatted_address || null;
}

async function patchDireccion(token, id, direccion) {
  const url = `${DOCS_URL}/${id}?updateMask.fieldPaths=direccion`;
  const res = await fetch(url, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields: { direccion: { stringValue: direccion } } }),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(`PATCH ${id} falló: ${data.error?.message || res.status}`);
  }
}

async function main() {
  if (!ADMIN_PASSWORD) throw new Error("Falta ADMIN_PASSWORD (contraseña del admin).");
  if (!GEOCODING_KEY) throw new Error("Falta GEOCODING_KEY (key del Geocoding API).");

  console.log(`Proyecto: ${PROJECT_ID} · admin: ${ADMIN_EMAIL}`);
  console.log(DRY_RUN ? "MODO DRY-RUN (no escribe)\n" : "MODO ESCRITURA\n");

  const token = await login();
  console.log("Login OK.");

  const reportes = await listarReportes(token);
  console.log(`${reportes.length} reportes leídos.`);

  const candidatos = reportes.filter((r) => {
    if (r.lat == null || r.lng == null) return false; // sin coords no hay geocoding
    return REGEOCODE_ALL || esGenerica(r.direccion);
  });
  console.log(`${candidatos.length} a re-geocodificar${REGEOCODE_ALL ? " (TODOS)" : " (genéricos/vacíos)"}.\n`);

  let ok = 0, saltados = 0, errores = 0;
  for (const r of candidatos) {
    try {
      const nueva = await geocodificar(r.lat, r.lng);
      if (!nueva) { console.log(`- ${r.id}: sin resultado, salto`); saltados++; continue; }
      if (nueva.trim() === (r.direccion || "").trim()) { saltados++; continue; }
      if (DRY_RUN) {
        console.log(`~ ${r.id}: "${r.direccion}" -> "${nueva}"`);
      } else {
        await patchDireccion(token, r.id, nueva);
        console.log(`✓ ${r.id}: "${nueva}"`);
      }
      ok++;
      await sleep(120); // gentileza con la API de geocoding
    } catch (e) {
      console.log(`✗ ${r.id}: ${e.message}`);
      errores++;
    }
  }

  console.log(`\nListo. ${DRY_RUN ? "Simulados" : "Actualizados"}: ${ok} · Saltados: ${saltados} · Errores: ${errores}`);
}

main().catch((e) => {
  console.error("ERROR:", e.message);
  process.exit(1);
});
