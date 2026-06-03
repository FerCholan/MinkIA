package com.moviles.minkia.data.model

/** Modelos del módulo administrador (Épica 5). Datos mock por ahora. */

/** Tarjeta de KPI del panel (A01). */
data class KpiAdmin(
    val valor: String,
    val etiqueta: String,
    val delta: String
)

/** Zona afectada del panel (A01) y de puntos críticos (A03). */
data class ZonaAfectada(
    val nombre: String,
    val focos: Int,
    val severidad: Severidad
)

/** Reporte entrante de la bandeja de alertas (A02). */
data class AlertaAdmin(
    val direccion: String,
    val tiempoTexto: String,
    val agrupados: Int,
    val severidad: Severidad,
    val nuevo: Boolean
)

/** Parada de una ruta de recolección (A05). */
data class ParadaRuta(
    val orden: Int,
    val nombre: String,
    val focos: Int,
    val distanciaKm: Double,
    val severidad: Severidad
)

/** Agregado del panel del administrador (A01): KPIs y zonas más afectadas. */
data class PanelAdmin(
    val kpis: List<KpiAdmin>,
    val zonas: List<ZonaAfectada>
)
