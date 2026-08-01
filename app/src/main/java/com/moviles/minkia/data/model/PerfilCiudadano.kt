package com.moviles.minkia.data.model

/**
 * Perfil y avance de gamificación (Minka digital) del ciudadano (mockup C17).
 * Representa los datos que mañana llegarán del backend; por eso los textos de
 * nombre/rol viven acá (son dato, no etiqueta de UI).
 */
data class PerfilCiudadano(
    val iniciales: String,
    val nombre: String,
    val rol: String,
    val reportes: Int,
    val resueltos: Int,
    val puntosMinka: Int,
    val nivel: Int,             // 1..5, para pintar las insignias de nivel
    val nivelTexto: String,
    val puntosNivelActual: Int,
    val puntosNivelObjetivo: Int,
    val faltanTexto: String
)
