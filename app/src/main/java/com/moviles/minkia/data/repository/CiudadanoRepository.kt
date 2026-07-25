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
     * Busca un reporte por su ticket (pantalla de Detalle, FASE 3 de la migración a
     * Navigation Component: reemplaza a los once extras que antes recibía
     * DetalleActivity completos). MiReporte y FocoMapa no guardan el id de
     * documento de Firestore, solo el ticket visible ("#MK-..."); es el único
     * identificador que exponen hoy, así que es lo único recuperable para pedir
     * "el reporte completo" por un solo argumento de navegación.
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
    suspend fun obtenerReportePorTicket(ticket: String): MiReporte? {
        misReportes().firstOrNull { it.ticket == ticket }?.let { return it }
        val foco = focosMapa().firstOrNull { it.ticket == ticket } ?: return null
        return MiReporte(
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
