// Migra los reportes con el estado viejo "EN_RUTA" (eliminado en el refactor) a
// "RECIBIDO", que es a donde el código los rescata igual. Deja la base coherente
// con el enum actual. Se autentica como admin vía REST y parchea updateMask=estado.
// Requiere que el admin tenga rol:'admin' en usuarios/{uid} (si no, las reglas
// rechazan el update con 403).

const KEY = "AIzaSyCAxk5q1rL1e9itvmS-hktEW-tfm7l93UI";
const PID = "minkia-4a73d";
const EMAIL = process.env.ADMIN_EMAIL || "admin@minkia.pe";
const PASSWORD = process.env.ADMIN_PASSWORD || "123456";
const VIEJO = "EN_RUTA";
const NUEVO = "RECIBIDO";

const DOCS = `https://firestore.googleapis.com/v1/projects/${PID}/databases/(default)/documents/reportes`;

const login = await (await fetch(
  `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${KEY}`,
  { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD, returnSecureToken: true }) }
)).json();
if (!login.idToken) { console.error("Login falló:", login.error?.message); process.exit(1); }
const t = login.idToken;
console.log("Login OK.");

const docs = (await (await fetch(`${DOCS}?pageSize=300`, { headers: { Authorization: `Bearer ${t}` } })).json()).documents || [];
const objetivo = docs.filter(d => d.fields?.estado?.stringValue === VIEJO);
console.log(`${docs.length} reportes · ${objetivo.length} con estado "${VIEJO}" a migrar.\n`);

let ok = 0, err = 0;
for (const d of objetivo) {
  const id = d.name.split("/").pop();
  const res = await fetch(`${DOCS}/${id}?updateMask.fieldPaths=estado`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${t}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields: { estado: { stringValue: NUEVO } } }),
  });
  if (res.ok) { console.log(`✓ ${id} -> ${NUEVO}`); ok++; }
  else { const e = await res.json().catch(() => ({})); console.log(`✗ ${id}: ${e.error?.message || res.status}`); err++; }
}
console.log(`\nListo. Migrados: ${ok} · Errores: ${err}`);
if (err > 0) console.log("Si son 403: falta poner rol:'admin' en usuarios/{uid} del admin (consola Firebase).");
