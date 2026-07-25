package com.moviles.minkia.data.model

/**
 * Un punto crítico de acumulación de residuos reportado por la comunidad.
 * Modelo de dominio; por ahora se alimenta de datos locales (mock).
 */
data class PuntoCritico(
    val id: String,
    val direccion: String,
    val referencia: String,
    val distanciaMetros: Int,
    val cantidadReportes: Int,
    val severidad: Severidad,
    val fotoUrl: String = ""
)

enum class Severidad {
    ALTA, MEDIA, BAJA
}
