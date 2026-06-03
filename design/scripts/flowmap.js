// Orden de flujo MinkIA. Cada entrada: [título exacto del caption, slug del archivo]
const CITIZEN = [
  ['Splash / Bienvenida', 'splash'],
  ['Onboarding / Guías', 'onboarding'],
  ['Iniciar sesión', 'login'],
  ['Crear cuenta', 'registro'],
  ['Recuperar contraseña', 'recuperar-contrasena'],
  ['Permisos (cámara · ubicación)', 'permisos'],
  ['Inicio · Mapa de calor', 'inicio-mapa-calor'],
  ['Explorar · Mapa fullscreen', 'explorar-mapa'],
  ['Cámara · Captura', 'camara-captura'],
  ['Análisis con IA (YOLOv8)', 'analisis-ia'],
  ['Formulario de reporte', 'formulario-reporte'],
  ['Confirmación · Sync', 'confirmacion-sync'],
  ['Mis reportes (historial)', 'mis-reportes'],
  ['Estado vacío (sin reportes)', 'estado-vacio'],
  ['Detalle / Seguimiento (ciudadano)', 'detalle-seguimiento'],
  ['Notificaciones', 'notificaciones'],
  ['Perfil + Minka digital', 'perfil-minka'],
  ['Configuración', 'configuracion'],
];
const ADMIN = [
  ['Panel administrador (dashboard)', 'panel-dashboard'],
  ['Bandeja de Alertas (admin)', 'bandeja-alertas'],
  ['Puntos críticos · Clustering', 'puntos-criticos-clustering'],
  ['Detalle · Validación IA', 'detalle-validacion'],
  ['Planificación de rutas', 'planificacion-rutas'],
];
const pad = n => ('0' + n).slice(-2);
const MAP = {};
CITIZEN.forEach(([t, slug], i) => { MAP[t] = { role: 'ciudadano', seq: i + 1, slug, name: 'C' + pad(i + 1) + '-' + slug }; });
ADMIN.forEach(([t, slug], i) => { MAP[t] = { role: 'admin', seq: i + 1, slug, name: 'A' + pad(i + 1) + '-' + slug }; });
const lookupByTitle = t => MAP[t] || null;
module.exports = { CITIZEN, ADMIN, lookupByTitle, pad };
