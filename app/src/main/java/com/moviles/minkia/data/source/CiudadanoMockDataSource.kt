package com.moviles.minkia.data.source

import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.data.model.PuntoCritico
import com.moviles.minkia.data.model.ResumenCiudadano
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.TipoNotificacion
import kotlinx.coroutines.delay

/**
 * Fuente de datos local de prueba. Simula la latencia de una llamada remota
 * con un pequeño delay, para que el ViewModel y la UI manejen estados de carga
 * reales desde ya. Cuando exista el backend (Firebase/REST), se reemplaza esta
 * clase sin tocar el repositorio ni el ViewModel.
 */
class CiudadanoMockDataSource {

    suspend fun obtenerResumen(): ResumenCiudadano {
        delay(600) // simula la red

        val puntos = listOf(
            PuntoCritico(
                id = "PC-001",
                direccion = "Jr. Elías Aguirre 450",
                referencia = "A 220 m de tu ubicación",
                distanciaMetros = 220,
                cantidadReportes = 5,
                severidad = Severidad.ALTA
            ),
            PuntoCritico(
                id = "PC-002",
                direccion = "Av. Pardo 1280",
                referencia = "A 540 m de tu ubicación",
                distanciaMetros = 540,
                cantidadReportes = 3,
                severidad = Severidad.MEDIA
            ),
            PuntoCritico(
                id = "PC-003",
                direccion = "Calle Bolognesi 320",
                referencia = "A 910 m de tu ubicación",
                distanciaMetros = 910,
                cantidadReportes = 2,
                severidad = Severidad.BAJA
            )
        )

        return ResumenCiudadano(
            puntosActivos = 62,
            tusReportes = 8,
            resueltos = 1200,
            focosCerca = puntos.size,
            puntosCercanos = puntos
        )
    }

    suspend fun misReportes(): List<MiReporte> {
        delay(500)
        return listOf(
            MiReporte("#MK-2041", "Jr. Elías Aguirre 450", "Hoy · 9:41", Severidad.ALTA, EstadoReporte.EN_PROCESO, 4.2, 5),
            MiReporte("#MK-1987", "Av. Pardo 1280", "26 may", Severidad.MEDIA, EstadoReporte.RESUELTO, 2.1, 2),
            MiReporte("#MK-1955", "Mz. H Lt. 4 · P. J. La Unión", "24 may", Severidad.ALTA, EstadoReporte.RECIBIDO, 6.0, 1),
            MiReporte("#MK-1902", "Calle Tacna 320", "21 may", Severidad.BAJA, EstadoReporte.DUPLICADO, 1.0, 0)
        )
    }

    suspend fun obtenerPerfil(): PerfilCiudadano {
        delay(400)
        return PerfilCiudadano(
            iniciales = "FC",
            nombre = "Fernando Cholán",
            rol = "Vecino fiscalizador · Nivel 3",
            reportes = 8,
            resueltos = 5,
            puntosMinka = 340,
            nivelTexto = "Nivel 3 · Guardián del barrio",
            puntosNivelActual = 340,
            puntosNivelObjetivo = 500,
            faltanTexto = "Te faltan 160 puntos para ser Héroe del barrio"
        )
    }

    suspend fun notificaciones(): List<Notificacion> {
        delay(400)
        return listOf(
            Notificacion(TipoNotificacion.VALIDADO, "Tu reporte #MK-2041 fue validado por la administración.", "Hace 2 horas", "HOY"),
            Notificacion(TipoNotificacion.INSIGNIA, "Ganaste la insignia \"Reportero del mes\".", "Hace 5 horas", "HOY"),
            Notificacion(TipoNotificacion.RESUELTO, "¡Resuelto! El foco de Av. Pardo 1280 fue limpiado.", "Hace 1 día", "ESTA SEMANA"),
            Notificacion(TipoNotificacion.UNIFICADO, "Tu reporte fue unificado con otros 4 vecinos.", "Hace 3 días", "ESTA SEMANA")
        )
    }
}
