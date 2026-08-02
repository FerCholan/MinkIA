package com.moviles.minkia.data.source

import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.Severidad
import kotlinx.coroutines.delay

/**
 * Inferencia de prueba del modelo de visión. Simula el tiempo de cómputo en el
 * dispositivo y devuelve una detección plausible. Cuando exista el modelo real
 * (YOLOv8/TFLite), se reemplaza esta clase cargando el .tflite y corriendo la
 * inferencia sobre el bitmap, sin afectar al repositorio ni a la UI.
 */
class AnalisisMockDataSource : AnalisisDataSource {

    override suspend fun analizar(rutaFoto: String): ResultadoAnalisis {
        delay(2200) // simula la inferencia en el dispositivo

        return ResultadoAnalisis(
            tipo = "Basura acumulada",
            confianza = 86,
            severidad = Severidad.ALTA,
            porcentajeCobertura = 45
        )
    }
}
