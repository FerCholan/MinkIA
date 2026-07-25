package com.moviles.minkia.data.model

/**
 * Resumen de la actividad del ciudadano que se muestra en la pantalla de inicio:
 * sus indicadores personales y los puntos críticos cercanos.
 */
data class ResumenCiudadano(
    val puntosActivos: Int,
    val tusReportes: Int,
    val resueltos: Int,
    val focosCerca: Int,
    val puntosCercanos: List<PuntoCritico>,
    val nombre: String = ""
)
