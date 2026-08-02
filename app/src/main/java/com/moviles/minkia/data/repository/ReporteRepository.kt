package com.moviles.minkia.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.ReportePendiente
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.ResultadoRegistro
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.source.AnalisisDataSource
import com.moviles.minkia.data.source.AnalisisMockDataSource
import com.moviles.minkia.data.source.ReporteFirestoreDataSource
import com.moviles.minkia.data.source.ResultadoEnvio
import com.moviles.minkia.data.sync.CambiosReportes
import com.moviles.minkia.data.sync.SincronizadorReportes
import java.util.UUID

/**
 * Punto único de acceso al flujo de reporte: análisis de la foto y el guardado
 * OFFLINE-FIRST del reporte. La UI habla solo con el repositorio.
 *
 * Guardado offline-first: con internet se envía directo (feedback inmediato); sin
 * internet o si el envío falla de forma transitoria, el reporte va a la cola local
 * ([ColaReportes]) y se reintenta cuando vuelva la conexión.
 *
 * El desenlace se declara en [ResultadoRegistro] y NO se confirma nada que no haya
 * quedado guardado: un rechazo permanente de Firestore (regla de seguridad, dato
 * inválido, sesión vencida) devuelve [ResultadoRegistro.Error], no un ticket.
 */
class ReporteRepository(
    private val analisisDataSource: AnalisisDataSource = AnalisisMockDataSource(),
    private val reporteDataSource: ReporteFirestoreDataSource = ReporteFirestoreDataSource(),
    private val perfilRepository: PerfilRepository = PerfilRepository()
) {
    suspend fun analizar(rutaFoto: String): ResultadoAnalisis =
        analisisDataSource.analizar(rutaFoto)

    /**
     * Ticket VISIBLE del reporte, derivado de su id (un UUID v4).
     *
     * Antes era `"#MK-" + (2000 + System.currentTimeMillis() % 8000)`: ocho mil
     * valores posibles y un ciclo completo cada ocho segundos, o sea dos reportes
     * hechos con 8 s de diferencia compartían ticket. Con doscientos reportes en la
     * comunidad la probabilidad de que existieran dos repetidos era del 92 %, y como
     * el ticket llegó a usarse para ABRIR un reporte, una colisión mostraba el
     * reporte de otro vecino.
     *
     * Ahora sale del mismo UUID que identifica al documento: ocho dígitos hex, más
     * de cuatro mil millones de combinaciones, y sin depender del reloj.
     */
    private fun ticketDe(id: String): String =
        "#MK-" + id.filter { it.isLetterOrDigit() }.take(TICKET_LARGO).uppercase()

    suspend fun guardar(
        tipo: String,
        severidad: Severidad,
        descripcion: String,
        direccion: String,
        zona: String,
        latitud: Double,
        longitud: Double,
        fotoPath: String?,
        porcentajeCobertura: Int = 0,
        confianza: Int = 0
    ): ResultadoRegistro {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("No hay sesión activa")

        // El id local es TAMBIÉN el id del documento en Firestore (ver
        // ReporteFirestoreDataSource.enviarReporte): es la identidad del reporte.
        val id = UUID.randomUUID().toString()
        val ticket = ticketDe(id)

        // Autor a mostrar: el apodo si lo activó, si no su nombre real (privacidad).
        val autor = try { perfilRepository.nombreVisible() } catch (e: Exception) { "" }
        // Nivel del autor al reportar: sella su insignia en el reporte.
        val autorNivel = try { reporteDataSource.nivelUsuario() } catch (e: Exception) { 1 }
        val pendiente = ReportePendiente(
            id = id,
            userId = uid,
            ticket = ticket,
            tipo = tipo,
            severidad = severidad.name,
            descripcion = descripcion,
            direccion = direccion,
            zona = zona,
            latitud = latitud,
            longitud = longitud,
            porcentajeCobertura = porcentajeCobertura,
            confianza = confianza,
            fotoPath = fotoPath,
            creadoEn = System.currentTimeMillis(),
            autor = autor,
            autorNivel = autorNivel
        )

        // SIEMPRE se intenta el envío directo. Antes esto estaba condicionado a
        // SincronizadorReportes.hayInternet(), y un falso negativo de esa función
        // (le pasaba con datos móviles, ver su KDoc) encolaba en silencio el
        // reporte de un vecino que SÍ tenía internet: el reporte no aparecía en
        // ninguna lista y la única señal era el cartel de "sin conexión".
        //
        // Preguntar antes no aportaba nada: si de verdad no hay red, el intento
        // falla solo y rápido, enviarReporte lo clasifica como REINTENTAR y el
        // reporte termina igual en la cola. La diferencia es que ahora la
        // respuesta la da la RED, no una suposición nuestra.
        //
        // Según el resultado: ENVIADO no se encola; REINTENTAR (sin red /
        // transitorio) va a la cola; DESCARTAR (fallo permanente, p. ej. reglas)
        // NO se encola, para no reintentar por siempre un envío que jamás va a pasar.
        var resultado = reporteDataSource.enviarReporte(pendiente)

        // UN segundo intento inmediato si el primero falló de forma transitoria
        // PERO hay red. Ese es exactamente el caso de haber cambiado de wifi a
        // datos con la foto recién tomada: la primera petición se va por un
        // socket que quedó muerto en la red anterior y falla, aunque el teléfono
        // esté navegando perfecto. Después de ese fallo la conexión rota ya salió
        // del pool, así que el segundo intento abre una nueva sobre la red buena
        // y normalmente pasa.
        //
        // Reintentar es seguro porque el envío es IDEMPOTENTE: el id del
        // documento es el id local del reporte, así que un .set() repetido
        // reescribe el mismo documento en vez de crear un duplicado (ver
        // ReporteFirestoreDataSource.enviarReporte).
        //
        // Sin red no se reintenta: sería hacer esperar al vecino para nada,
        // porque el reporte va a la cola igual y sale solo al volver la conexión.
        if (resultado == ResultadoEnvio.REINTENTAR && SincronizadorReportes.hayInternet()) {
            resultado = reporteDataSource.enviarReporte(pendiente)
        }

        val reporte = Reporte(
            ticket = ticket,
            tipo = tipo,
            severidad = severidad,
            descripcion = descripcion,
            direccion = direccion,
            latitud = latitud,
            longitud = longitud,
            fotoPath = fotoPath
        )

        // Cada desenlace se declara tal cual es. DESCARTAR no encola ni confirma:
        // devolver un ticket ahí sería mentirle al vecino sobre un reporte perdido.
        return when (resultado) {
            ResultadoEnvio.ENVIADO -> {
                // El reporte YA está en el servidor: avisar para que el mapa, el
                // inicio y la lista lo muestren sin esperar a nada (ver
                // CambiosReportes). Sin esto, el vecino leía "¡Reporte enviado!"
                // y después no lo encontraba en el mapa.
                CambiosReportes.notificar()
                ResultadoRegistro.Enviado(reporte)
            }
            ResultadoEnvio.REINTENTAR -> {
                ColaReportes.encolar(pendiente)
                ResultadoRegistro.Pendiente(reporte)
            }
            ResultadoEnvio.DESCARTAR -> ResultadoRegistro.Error(MENSAJE_RECHAZO)
        }
    }

    private companion object {
        /** Dígitos hex del id que entran en el ticket visible. 8 = 4.294.967.296 combinaciones. */
        const val TICKET_LARGO = 8

        /**
         * Mensaje del rechazo permanente. Habla de lo que el vecino PUEDE hacer
         * (revisar su sesión, volver a intentar) sin exponer detalles internos de
         * las reglas de seguridad.
         */
        const val MENSAJE_RECHAZO =
            "No se pudo registrar el reporte. Verifica que tu sesión siga activa y vuelve a intentarlo."
    }
}
