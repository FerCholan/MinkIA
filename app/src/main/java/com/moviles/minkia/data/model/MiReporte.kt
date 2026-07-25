package com.moviles.minkia.data.model

/**
 * Un reporte hecho por el ciudadano, para el historial (mockup C13/C15).
 */
data class MiReporte(
    val ticket: String,
    val direccion: String,
    val fechaTexto: String,
    val severidad: Severidad,
    val estado: EstadoReporte,
    val areaM2: Double = 0.0,
    val vecinos: Int = 0,
    val fotoUrl: String = "",
    val tipo: String = "",
    val confianza: Int = 0,
    val autor: String = "",
    val autorNivel: Int = 1
)

/**
 * Estado del seguimiento de un reporte. Flujo de moderación:
 * RECIBIDO -> EN_PROCESO (el admin valida) -> RESUELTO (el admin aprueba).
 * Ramas laterales: DUPLICADO (unificado con otro) y ANULADO (troll/falso, se
 * conserva para auditoría en vez de borrarse). Reportes viejos con "EN_RUTA"
 * caen a RECIBIDO al parsear (ver ReporteFirestoreDataSource.parseEstado).
 */
enum class EstadoReporte(val etiqueta: String) {
    RECIBIDO("Recibido"),
    EN_PROCESO("En proceso"),
    RESUELTO("Resuelto"),
    DUPLICADO("Duplicado"),
    ANULADO("Anulado");

    /** Para el filtro de la lista: resueltos vs pendientes. */
    val esResuelto: Boolean get() = this == RESUELTO
    val esPendiente: Boolean get() = this == RECIBIDO || this == EN_PROCESO
}
