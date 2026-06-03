package com.moviles.minkia.data.source

import com.moviles.minkia.data.model.ResultadoAnalisis

/**
 * Contrato del análisis de la foto. Abstrae el origen: hoy hay un mock y la
 * inferencia real con YOLOv8/TFLite. El repositorio depende de esta interfaz, no
 * de una implementación concreta (inversión de dependencias).
 */
interface AnalisisDataSource {
    suspend fun analizar(rutaFoto: String): ResultadoAnalisis
}
