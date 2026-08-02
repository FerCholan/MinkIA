package com.moviles.minkia.data.model

/**
 * Un reporte que todavía NO se pudo enviar al servidor (creado sin internet o con
 * un fallo de red) y está en la cola local esperando reintento. Guarda TODO lo
 * necesario para enviarlo tal cual cuando vuelva la conexión, incluida la ruta
 * local de la foto (copiada a un lugar persistente para que no se pierda).
 */
data class ReportePendiente(
    val id: String,          // id local (UUID), para ubicarlo en la cola
    val userId: String,
    val ticket: String,
    val tipo: String,
    val severidad: String,   // Severidad.name
    val descripcion: String,
    val direccion: String,
    val zona: String,
    val latitud: Double,
    val longitud: Double,
    val porcentajeCobertura: Int,
    val confianza: Int,
    val fotoPath: String?,   // ruta local persistente de la foto (o null si no hay)
    val creadoEn: Long,
    val autor: String = "",  // nombre a mostrar (apodo si lo activó, si no el real)
    val autorNivel: Int = 1  // nivel del autor al reportar, para mostrar su insignia
)
