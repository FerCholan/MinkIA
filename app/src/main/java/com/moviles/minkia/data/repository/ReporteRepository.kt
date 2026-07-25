package com.moviles.minkia.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.ReportePendiente
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.source.AnalisisDataSource
import com.moviles.minkia.data.source.AnalisisMockDataSource
import com.moviles.minkia.data.source.ReporteFirestoreDataSource
import com.moviles.minkia.data.source.ResultadoEnvio
import com.moviles.minkia.data.sync.SincronizadorReportes
import java.util.UUID

/**
 * Punto único de acceso al flujo de reporte: análisis de la foto y el guardado
 * OFFLINE-FIRST del reporte. La UI habla solo con el repositorio.
 *
 * Guardado offline-first: con internet se envía directo (feedback inmediato); sin
 * internet o si el envío falla, el reporte va a la cola local ([ColaReportes]) y se
 * reintenta cuando vuelva la conexión. El reporte NUNCA se pierde: siempre se
 * confirma al vecino con su ticket.
 */
class ReporteRepository(
    private val analisisDataSource: AnalisisDataSource = AnalisisMockDataSource(),
    private val reporteDataSource: ReporteFirestoreDataSource = ReporteFirestoreDataSource(),
    private val perfilRepository: PerfilRepository = PerfilRepository()
) {
    suspend fun analizar(rutaFoto: String): ResultadoAnalisis =
        analisisDataSource.analizar(rutaFoto)

    suspend fun guardar(
        tipo: String,
        severidad: Severidad,
        descripcion: String,
        direccion: String,
        zona: String,
        latitud: Double,
        longitud: Double,
        fotoPath: String?,
        areaM2: Double = 0.0,
        confianza: Int = 0
    ): Reporte {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("No hay sesión activa")
        val ticket = "#MK-" + (2000 + (System.currentTimeMillis() % 8000))
        // Autor a mostrar: el apodo si lo activó, si no su nombre real (privacidad).
        val autor = try { perfilRepository.nombreVisible() } catch (e: Exception) { "" }
        // Nivel del autor al reportar: sella su insignia en el reporte.
        val autorNivel = try { reporteDataSource.nivelUsuario() } catch (e: Exception) { 1 }
        val pendiente = ReportePendiente(
            id = UUID.randomUUID().toString(),
            userId = uid,
            ticket = ticket,
            tipo = tipo,
            severidad = severidad.name,
            descripcion = descripcion,
            direccion = direccion,
            zona = zona,
            latitud = latitud,
            longitud = longitud,
            areaM2 = areaM2,
            confianza = confianza,
            fotoPath = fotoPath,
            creadoEn = System.currentTimeMillis(),
            autor = autor,
            autorNivel = autorNivel
        )

        // Con internet, intento directo (el vecino ve su reporte al toque). Según el
        // resultado: ENVIADO no se encola; REINTENTAR (sin red / transitorio) va a la
        // cola; DESCARTAR (fallo permanente, p. ej. reglas) NO se encola, para no
        // reintentar por siempre un envío que jamás va a pasar.
        val resultado = if (SincronizadorReportes.hayInternet()) {
            reporteDataSource.enviarReporte(pendiente)
        } else {
            ResultadoEnvio.REINTENTAR
        }
        if (resultado == ResultadoEnvio.REINTENTAR) ColaReportes.encolar(pendiente)

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
