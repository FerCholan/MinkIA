 package com.moviles.minkia.data.model

/** Modelos del módulo administrador (Épica 5). Datos REALES desde Firestore. */

/**
 * Zona afectada de puntos críticos (A03): agrupación de focos por dirección. Trae
 * una coordenada representativa (el centro de sus focos) para poder enfocar el mapa
 * en la zona al tocarla.
 */
data class ZonaAfectada(
    val nombre: String,
    val focos: Int,
    val severidad: Severidad,
    val latitud: Double = 0.0,
    val longitud: Double = 0.0
)

/**
 * Reporte entrante de la bandeja de moderación (A02). Trae coordenadas y hora
 * REALES para que el admin decida sobre el terreno (mapa, dirección, momento).
 */
data class AlertaAdmin(
    val id: String,
    val direccion: String,
    val tiempoTexto: String,
    val agrupados: Int,
    val severidad: Severidad,
    val nuevo: Boolean,
    val ticket: String = "",
    val estado: EstadoReporte = EstadoReporte.RECIBIDO,
    val fotoUrl: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val fechaHoraTexto: String = "—",
    val tipo: String = "",
    val descripcion: String = "",
    val areaM2: Double = 0.0,
    val confianza: Int = 0
)

/**
 * Fila del reporte municipal (A06): un reporte con sus columnas clave, ya
 * formateadas. Es la fuente única que alimenta la pantalla, el CSV y el PDF.
 */
data class FilaReporte(
    /** Id del documento: identidad real de la fila (el ticket puede repetirse). */
    val id: String,
    val ticket: String,
    val direccion: String,
    val tipo: String,
    val severidad: Severidad,
    val estado: EstadoReporte,
    val fechaHoraTexto: String,
    val latitud: Double,
    val longitud: Double
)
