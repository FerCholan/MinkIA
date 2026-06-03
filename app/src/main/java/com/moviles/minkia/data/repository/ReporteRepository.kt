package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.source.AnalisisDataSource
import com.moviles.minkia.data.source.AnalisisMockDataSource
import com.moviles.minkia.data.source.ReporteMockDataSource

/**
 * Punto único de acceso al flujo de reporte: análisis de la foto y el guardado
 * (offline-first) del reporte. La UI habla solo con el repositorio; cambiar el
 * origen (mock -> modelo real / backend) se hace acá.
 */
class ReporteRepository(
    private val analisisDataSource: AnalisisDataSource = AnalisisMockDataSource(),
    private val reporteDataSource: ReporteMockDataSource = ReporteMockDataSource()
) {
    suspend fun analizar(rutaFoto: String): ResultadoAnalisis =
        analisisDataSource.analizar(rutaFoto)

    suspend fun guardar(
        tipo: String,
        severidad: Severidad,
        descripcion: String,
        direccion: String,
        latitud: Double,
        longitud: Double,
        fotoPath: String?
    ): Reporte = reporteDataSource.guardar(
        tipo, severidad, descripcion, direccion, latitud, longitud, fotoPath
    )
}
