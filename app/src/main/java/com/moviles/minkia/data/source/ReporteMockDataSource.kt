package com.moviles.minkia.data.source

import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.Severidad
import kotlinx.coroutines.delay

/**
 * Persistencia de prueba de reportes. Simula el guardado local (offline-first) y
 * asigna un ticket. Cuando exista el backend, este guardado encolaría el reporte
 * y lo sincronizaría al volver la conexión, sin tocar el repositorio ni la UI.
 */
class ReporteMockDataSource {

    suspend fun guardar(
        tipo: String,
        severidad: Severidad,
        descripcion: String,
        direccion: String,
        latitud: Double,
        longitud: Double,
        fotoPath: String?
    ): Reporte {
        delay(1200) // simula guardar y encolar para sincronizar

        val ticket = "#MK-" + (2000 + (System.currentTimeMillis() % 1000)).toString()
        return Reporte(
            ticket = ticket,
            tipo = tipo,
            severidad = severidad,
            descripcion = descripcion,
            direccion = direccion,
            latitud = latitud,
            longitud = longitud,
            fotoPath = fotoPath
        )
    }
}
