package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.FilaReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.ZonaAfectada
import com.moviles.minkia.data.source.ReporteFirestoreDataSource

/**
 * Acceso a datos del módulo administrador (Épica 5), reducido a moderación +
 * reportería. Todo REAL desde Firestore (misma colección `reportes` que el
 * ciudadano). Las ACCIONES de moderación necesitan rol admin (reglas Firestore).
 */
class AdminRepository(
    private val reporteDataSource: ReporteFirestoreDataSource = ReporteFirestoreDataSource()
) {

    /** Bandeja de reportes pendientes (A02), REAL desde Firestore. */
    suspend fun alertas(): List<AlertaAdmin> = reporteDataSource.alertasAdmin()

    /**
     * Busca una alerta puntual por id (pantalla de Validación, FASE 3 de la
     * migración a Navigation Component: reemplaza a los trece extras que antes
     * recibía ValidacionActivity completos). AlertaAdmin.id YA es el id real del
     * documento de Firestore (el mismo que usan resolver/validar/anular más
     * abajo), a diferencia de MiReporte/FocoMapa del lado ciudadano.
     *
     * No agrega una consulta nueva: reusa [alertas] (la misma bandeja que ya
     * lista AlertasFragment) y filtra por id. Alcanza porque el único camino de
     * entrada real a esta pantalla es tocar un ítem de esa bandeja, siempre
     * pendiente (los reportes ya resueltos/duplicados/anulados salen de
     * [alertas], ver su KDoc, y por eso nunca llegan a ofrecerse como ítem).
     */
    suspend fun obtenerAlertaPorId(id: String): AlertaAdmin? =
        alertas().firstOrNull { it.id == id }

    /** Zonas afectadas bajo el mapa de puntos críticos (A03). */
    suspend fun zonas(): List<ZonaAfectada> = reporteDataSource.zonasAfectadas()

    /** Validar el reporte (-> EN_PROCESO). Necesita rol admin. */
    suspend fun validar(id: String) = reporteDataSource.validarReporte(id)

    /** Aprobar resuelto (-> RESUELTO): el foco fue limpiado. Necesita rol admin. */
    suspend fun resolver(id: String) = reporteDataSource.resolverReporte(id)

    /** Marcar el reporte como duplicado. Necesita rol admin. */
    suspend fun marcarDuplicado(id: String) = reporteDataSource.marcarDuplicado(id)

    /** Anular (troll/falso): marca ANULADO sin borrar. Necesita rol admin. */
    suspend fun anular(id: String) = reporteDataSource.anularReporte(id)

    /** Actualizar el nivel (severidad) del reporte. Necesita rol admin. */
    suspend fun actualizarNivel(id: String, severidad: Severidad) =
        reporteDataSource.actualizarSeveridad(id, severidad)

    /** Reporte municipal filtrado por rango de fechas y nivel (A06). */
    suspend fun reportes(desde: Long, hasta: Long, severidad: Severidad?): List<FilaReporte> =
        reporteDataSource.reportesFiltrados(desde, hasta, severidad)

    /** CSV municipal a partir de filas ya filtradas (para exportar/compartir). */
    fun csv(filas: List<FilaReporte>): String = reporteDataSource.csvDe(filas)
}
