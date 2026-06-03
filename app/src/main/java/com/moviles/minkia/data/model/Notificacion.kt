package com.moviles.minkia.data.model

/**
 * Una notificación del ciudadano (mockup C16). El [tipo] define el ícono y color.
 */
data class Notificacion(
    val tipo: TipoNotificacion,
    val texto: String,
    val tiempoTexto: String,
    val grupo: String // "HOY", "ESTA SEMANA"
)

enum class TipoNotificacion {
    VALIDADO, INSIGNIA, RESUELTO, UNIFICADO
}
