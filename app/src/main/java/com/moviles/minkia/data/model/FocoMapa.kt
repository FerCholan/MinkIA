package com.moviles.minkia.data.model

/**
 * Un foco de basura geolocalizado, para pintar en el mapa (C08) y el mapa de
 * calor del Inicio (C07). Sale de los reportes activos de la comunidad.
 */
data class FocoMapa(
    val id: String,
    val latitud: Double,
    val longitud: Double,
    val severidad: Severidad,
    val direccion: String,
    val tipo: String,
    val ticket: String = "",
    val estado: EstadoReporte = EstadoReporte.RECIBIDO,
    val fotoUrl: String = "",
    val zona: String = "",
    val confianza: Int = 0,
    val areaM2: Double = 0.0,
    val autor: String = "",
    val autorNivel: Int = 1
)
