# MinkIA — Backlog de interfaces

Trabajo organizado en **rebanadas verticales**: cada pantalla se entrega funcionando de punta a punta (vista + ViewModel + repo + datos mock) y se valida contra su mockup en `design/png/mockups/`.

**Definition of Done (DoD) por pantalla:**
- [ ] Se ve como el mockup correspondiente.
- [ ] Funciona end-to-end con su ViewModel (maneja Loading / Success / Error si aplica).
- [ ] Sigue el patrón de `ui/home/` (BaseFragment + BaseViewModel + UiState).
- [ ] Verificada en el emulador por el usuario.

Estados: ⬜ pendiente · 🟡 en curso · ✅ listo

---

## Épica 0 — Esqueleto de navegación
- ✅ Bottom navigation (Inicio · Mapa · FAB Reportar · Reportes · Perfil) + contenedor de fragments

## Épica 1 — Acceso
- ✅ C01 Splash
- ✅ C02 Onboarding
- ✅ C03 Login
- ✅ C04 Registro
- ✅ C05 Recuperar contraseña
- ✅ C06 Permisos

## Épica 2 — Núcleo: Reportar ⭐
- ✅ C09 Captura con la cámara
- ✅ C10 Análisis con IA (inferencia simulada; contrato listo para YOLOv8 real)
- ✅ C11 Formulario del reporte (GPS real best-effort)
- ✅ C12 Confirmación y sincronización

## Épica 3 — Seguimiento
- ✅ C13 Mis reportes (historial)
- ✅ C14 Estado vacío (integrado en C13, por filtro)
- ✅ C15 Detalle y seguimiento
- ✅ C16 Notificaciones

## Épica 4 — Mapa y Perfil
- ✅ C08 Explorar mapa (vista previa; mapa real = Google Maps SDK pendiente)
- ✅ C17 Perfil y Minka digital
- ✅ C18 Configuración

## Épica 5 — Administrador
- ✅ A01 Panel
- ✅ A02 Bandeja de alertas
- ✅ A03 Puntos críticos y agrupación
- ✅ A04 Detalle y validación con IA
- ✅ A05 Planificación de rutas

> Acceso admin: login con `admin@minkia.pe` / `123456` (ruteo por rol).

---

## Hecho
- ✅ C07 Inicio con mapa de calor (pantalla patrón de referencia)
- ✅ Fundación MVVM: `core/` (UiState, BaseViewModel, BaseFragment) + capa `data/` mock
