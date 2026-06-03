package com.moviles.minkia.data.model

/**
 * Reporte de un foco de basura listo para enviar. Se arma en el formulario
 * (C11) a partir del análisis y los datos del ciudadano. El [ticket] lo asigna
 * el repositorio al guardarlo.
 */
data class Reporte(
    val ticket: String,
    val tipo: String,
    val severidad: Severidad,
    val descripcion: String,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    val fotoPath: String?
)
