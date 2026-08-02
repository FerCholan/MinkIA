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
    /**
     * Porcentaje del encuadre de la foto cubierto por residuos, 0..100.
     *
     * Antes este campo era `areaM2` y se calculaba multiplicando la cobertura por
     * un área de encuadre ASUMIDA de 6 m². No eran metros cuadrados: no hay
     * calibración de cámara, ni referencia métrica en la escena, ni distancia
     * conocida al foco. La app mostraba "Área estimada: 1,68 m²", un número con
     * apariencia de medición que en realidad era una cobertura porcentual
     * disfrazada, y sobre el que la municipalidad podía terminar tomando
     * decisiones. Ahora se informa lo que de verdad se mide.
     */
    val porcentajeCobertura: Int,
    val esBasura: Boolean = true // false si el modelo NO detectó residuos: no se reporta
)
