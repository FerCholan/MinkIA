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
    val vecinos: Int = 0
)

/** Estado del seguimiento de un reporte. */
enum class EstadoReporte(val etiqueta: String) {
    RECIBIDO("Recibido"),
    EN_PROCESO("En proceso"),
    EN_RUTA("En ruta"),
    RESUELTO("Resuelto"),
    DUPLICADO("Duplicado");

    /** Para el filtro de la lista: resueltos vs pendientes. */
    val esResuelto: Boolean get() = this == RESUELTO
    val esPendiente: Boolean get() = this == RECIBIDO || this == EN_PROCESO || this == EN_RUTA
}
