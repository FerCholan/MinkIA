package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.data.model.ResumenCiudadano
import com.moviles.minkia.data.source.ReporteFirestoreDataSource

/**
 * Punto único de acceso a los datos del ciudadano. La capa de UI (ViewModel)
 * habla solo con el repositorio, nunca con la fuente de datos directamente.
 * Resumen, perfil, mis reportes, focos del mapa y notificaciones vienen TODOS de
 * Firestore (ya no hay mock acá): el origen se cambia adentro sin tocar la UI.
 */
class CiudadanoRepository(
    private val reporteDataSource: ReporteFirestoreDataSource = ReporteFirestoreDataSource()
) {
    suspend fun obtenerResumen(): ResumenCiudadano = reporteDataSource.obtenerResumen()

    suspend fun obtenerPerfil(): PerfilCiudadano = reporteDataSource.obtenerPerfil()

    suspend fun misReportes(): List<MiReporte> = reporteDataSource.misReportes()

    suspend fun focosMapa(): List<FocoMapa> = reporteDataSource.obtenerFocosMapa()

    suspend fun notificaciones(): List<Notificacion> = reporteDataSource.notificacionesCiudadano()

    /**
     * Busca un reporte por el ID de su documento (pantalla de Detalle).
     *
     * Antes buscaba por TICKET, y ahí estaba el problema: el ticket es un texto
     * para el vecino, no una identidad. Se generaba a partir del reloj con solo
     * 8000 valores posibles, así que dos reportes distintos podían compartirlo; y
     * como esta búsqueda mira primero los reportes PROPIOS, una colisión hacía que
     * tocar el foco de otro vecino en el mapa abriera un reporte tuyo. El id de
     * documento es único por construcción y ya viajaba en [FocoMapa]; ahora
     * también en [MiReporte].
     *
     * No agrega una consulta nueva a Firestore: compone las DOS que ya existen y
     * ya usan Reportes y Mapa, sin tocar la fuente de datos.
     * 1) [misReportes]: los reportes del vecino logueado, en CUALQUIER estado
     *    (cubre entrar desde "Mis reportes", incluidos los resueltos).
     * 2) Si no aparece ahí, [focosMapa]: los focos PENDIENTES de toda la
     *    comunidad (cubre entrar desde el Mapa, con el reporte de otro vecino).
     *    Ese origen no trae "vecinos" (el Mapa nunca lo mandó, ni siquiera como
     *    extra): queda en 0, igual que el comportamiento de antes del refactor.
     */
    suspend fun obtenerReportePorId(id: String): MiReporte? {
        misReportes().firstOrNull { it.id == id }?.let { return it }
        val foco = focosMapa().firstOrNull { it.id == id } ?: return null
        return MiReporte(
            id = foco.id,
            ticket = foco.ticket,
            direccion = foco.direccion,
            fechaTexto = "",
            severidad = foco.severidad,
            estado = foco.estado,
            areaM2 = foco.areaM2,
            vecinos = 0,
            fotoUrl = foco.fotoUrl,
            tipo = foco.tipo,
            confianza = foco.confianza,
            autor = foco.autor,
            autorNivel = foco.autorNivel
        )
    }
}
