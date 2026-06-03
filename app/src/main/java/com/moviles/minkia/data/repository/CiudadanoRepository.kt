package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.data.model.ResumenCiudadano
import com.moviles.minkia.data.source.CiudadanoMockDataSource

/**
 * Punto único de acceso a los datos del ciudadano. La capa de UI (ViewModel)
 * habla solo con el repositorio, nunca con la fuente de datos directamente.
 * Así, cambiar el origen (mock -> Firebase/REST) se hace acá adentro, sin
 * afectar al resto de la aplicación.
 */
class CiudadanoRepository(
    private val dataSource: CiudadanoMockDataSource = CiudadanoMockDataSource()
) {
    suspend fun obtenerResumen(): ResumenCiudadano = dataSource.obtenerResumen()

    suspend fun obtenerPerfil(): PerfilCiudadano = dataSource.obtenerPerfil()

    suspend fun misReportes(): List<MiReporte> = dataSource.misReportes()

    suspend fun notificaciones(): List<Notificacion> = dataSource.notificaciones()
}
