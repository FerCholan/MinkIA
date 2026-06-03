package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.KpiAdmin
import com.moviles.minkia.data.model.PanelAdmin
import com.moviles.minkia.data.model.ParadaRuta
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.ZonaAfectada
import kotlinx.coroutines.delay

/**
 * Acceso a datos del módulo administrador (Épica 5). Mock por ahora; el día de
 * mañana lee del backend de gestión sin afectar a la UI.
 */
class AdminRepository {

    /** Agregado del panel (A01): KPIs y zonas en una sola carga. */
    suspend fun panel(): PanelAdmin = PanelAdmin(kpis = kpis(), zonas = zonasAfectadas())

    suspend fun kpis(): List<KpiAdmin> {
        delay(300)
        return listOf(
            KpiAdmin("47", "Reportes hoy", "+12%"),
            KpiAdmin("62", "Puntos críticos", "+3"),
            KpiAdmin("38", "Resueltos (sem.)", "+8%"),
            KpiAdmin("81%", "Tasa de atención", "")
        )
    }

    suspend fun zonasAfectadas(): List<ZonaAfectada> {
        delay(200)
        return listOf(
            ZonaAfectada("P. J. La Unión", 18, Severidad.ALTA),
            ZonaAfectada("Centro histórico", 12, Severidad.MEDIA),
            ZonaAfectada("Miraflores Alto", 7, Severidad.BAJA)
        )
    }

    suspend fun alertas(): List<AlertaAdmin> {
        delay(400)
        return listOf(
            AlertaAdmin("P. J. La Unión - Mz H", "Hace 4 min · 5 reportes agrupados", 5, Severidad.ALTA, true),
            AlertaAdmin("Jr. Bolognesi 540", "Hace 18 min · 3 reportes agrupados", 3, Severidad.MEDIA, true),
            AlertaAdmin("Av. Pardo 1280", "Hace 1 h · sin asignar", 1, Severidad.MEDIA, false),
            AlertaAdmin("Calle Tacna 320", "Hace 2 h · sin asignar", 1, Severidad.BAJA, false)
        )
    }

    suspend fun ruta(): List<ParadaRuta> {
        delay(300)
        return listOf(
            ParadaRuta(1, "P. J. La Unión - Mz H", 23, 2.1, Severidad.ALTA),
            ParadaRuta(2, "Jr. Bolognesi 540", 12, 1.4, Severidad.MEDIA),
            ParadaRuta(3, "Av. Pardo 1280", 8, 1.8, Severidad.MEDIA),
            ParadaRuta(4, "Centro histórico", 6, 1.2, Severidad.ALTA),
            ParadaRuta(5, "Miraflores Alto", 4, 1.9, Severidad.BAJA)
        )
    }
}
