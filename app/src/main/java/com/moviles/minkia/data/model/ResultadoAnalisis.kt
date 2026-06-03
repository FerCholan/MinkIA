package com.moviles.minkia.data.model

/**
 * Resultado del análisis de la foto por el modelo de visión (mockup C10). Hoy lo
 * produce una fuente simulada; el contrato queda listo para enchufar la
 * inferencia real (YOLOv8 en el dispositivo) sin tocar la UI ni el ViewModel.
 */
data class ResultadoAnalisis(
    val tipo: String,        // p. ej. "Basura acumulada"
    val confianza: Int,      // 0..100 (%)
    val severidad: Severidad,
    val areaM2: Double       // área estimada del foco
)
