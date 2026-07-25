package com.moviles.minkia.data.source

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.FilaReporte
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.model.PerfilCiudadano
import com.moviles.minkia.data.model.PuntoCritico
import com.moviles.minkia.data.model.TipoNotificacion
import com.moviles.minkia.data.model.ReportePendiente
import com.moviles.minkia.data.model.ResumenCiudadano
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.ZonaAfectada
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resultado de intentar enviar un reporte; la cola decide qué hacer con cada uno. */
enum class ResultadoEnvio { ENVIADO, REINTENTAR, DESCARTAR }

/** Códigos de Firestore que NO se arreglan reintentando (fallo permanente). */
private val ERRORES_PERMANENTES = setOf(
    FirebaseFirestoreException.Code.PERMISSION_DENIED,
    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
    FirebaseFirestoreException.Code.UNAUTHENTICATED
)

/**
 * Persistencia REAL de reportes en Firestore (colección `reportes`) + la foto en
 * Firebase Storage. Cada reporte queda atado al `userId` del vecino logueado.
 * Reemplaza al mock sin tocar el ViewModel ni la UI.
 */
class ReporteFirestoreDataSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Envía UN reporte pendiente al servidor: sube la foto a Cloudinary y crea el
     * doc en Firestore con los campos tal cual quedaron en la cola (incluido su
     * ticket). Devuelve ENVIADO / REINTENTAR (fallo transitorio: la cola lo conserva)
     * / DESCARTAR (fallo permanente: la cola lo saca para no reintentar por siempre).
     * Solo lanza si la corrutina se cancela. Es la fuente ÚNICA del envío (la usa el
     * guardado directo y el sincronizador de la cola).
     */
    suspend fun enviarReporte(p: ReportePendiente): ResultadoEnvio = try {
        val fotoUrl = CloudinaryUploader.subir(p.fotoPath)
        // Si el reporte tiene foto pero no se pudo subir (sin red), no lo mandamos a
        // medias: reintentar luego con la foto cuando vuelva internet.
        if (!p.fotoPath.isNullOrBlank() && fotoUrl.isBlank()) {
            ResultadoEnvio.REINTENTAR
        } else {
            // Idempotente: el doc id es el UUID local del reporte. Si un envío se
            // comitea en el servidor pero la respuesta se pierde (timeout) y la cola
            // reintenta, el .set() reescribe EL MISMO documento en vez de crear un
            // duplicado. `creadoEn` usa la fecha real de creación local (no la del
            // servidor): un reporte hecho offline conserva su cronología al sincronizar.
            db.collection(COLECCION).document(p.id).set(
                mapOf(
                    "userId" to p.userId,
                    "ticket" to p.ticket,
                    "tipo" to p.tipo,
                    "severidad" to p.severidad,
                    "descripcion" to p.descripcion,
                    "direccion" to p.direccion,
                    "zona" to p.zona,
                    "latitud" to p.latitud,
                    "longitud" to p.longitud,
                    "areaM2" to p.areaM2,
                    "confianza" to p.confianza,
                    "autor" to p.autor,
                    "autorNivel" to p.autorNivel,
                    "fotoUrl" to fotoUrl,
                    "estado" to EstadoReporte.RECIBIDO.name,
                    "creadoEn" to Timestamp(Date(p.creadoEn))
                )
            ).await()
            ResultadoEnvio.ENVIADO
        }
    } catch (e: CancellationException) {
        throw e // respetar la cancelación estructurada: no tragarla como fallo
    } catch (e: FirebaseFirestoreException) {
        // Permiso denegado / dato inválido no se arreglan reintentando -> descartar.
        // El resto (UNAVAILABLE, DEADLINE_EXCEEDED, etc.) es transitorio -> reintentar.
        if (e.code in ERRORES_PERMANENTES) ResultadoEnvio.DESCARTAR else ResultadoEnvio.REINTENTAR
    } catch (e: Exception) {
        ResultadoEnvio.REINTENTAR // red u otro fallo transitorio
    }

    /** Reportes del vecino logueado, ordenados del más nuevo al más viejo. */
    suspend fun misReportes(): List<MiReporte> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        // Solo filtro por userId (índice automático). El orden se hace en memoria
        // para no requerir un índice compuesto en Firestore.
        val snap = db.collection(COLECCION).whereEqualTo("userId", uid).get().await()
        return snap.documents
            .sortedByDescending { it.getTimestamp("creadoEn")?.toDate()?.time ?: 0L }
            .map { doc ->
                MiReporte(
                    ticket = doc.getString("ticket").orEmpty(),
                    direccion = doc.getString("direccion").orEmpty(),
                    fechaTexto = formatearFecha(doc.getTimestamp("creadoEn")?.toDate()),
                    severidad = parseSeveridad(doc.getString("severidad")),
                    estado = parseEstado(doc.getString("estado")),
                    areaM2 = doc.getDouble("areaM2") ?: 0.0,
                    vecinos = (doc.getLong("vecinos") ?: 0L).toInt(),
                    fotoUrl = doc.getString("fotoUrl").orEmpty(),
                    tipo = doc.getString("tipo").orEmpty(),
                    confianza = (doc.getLong("confianza") ?: 0L).toInt(),
                    autor = doc.getString("autor").orEmpty(),
                    autorNivel = (doc.getLong("autorNivel") ?: 1L).toInt()
                )
            }
    }

    /**
     * Resumen del Inicio (C07) calculado sobre TODOS los reportes de la comunidad:
     * focos activos, resueltos, tus reportes y los focos activos más recientes.
     */
    suspend fun obtenerResumen(): ResumenCiudadano {
        val uid = auth.currentUser?.uid
        val docs = db.collection(COLECCION).get().await().documents

        // "Activos" = focos PENDIENTES (RECIBIDO/EN_PROCESO), igual que el mapa y las
        // zonas. Antes usaba "!= RESUELTO", que contaba duplicados y anulados como
        // activos (mal). Resueltos sí es la cuenta directa de RESUELTO.
        val activos = docs.count { parseEstado(it.getString("estado")).esPendiente }
        val resueltos = docs.count { it.getString("estado") == EstadoReporte.RESUELTO.name }
        val mios = docs.count { it.getString("userId") == uid }

        val puntos = docs
            .filter { parseEstado(it.getString("estado")).esPendiente }
            .sortedByDescending { it.getTimestamp("creadoEn")?.toDate()?.time ?: 0L }
            .take(6)
            .map { doc ->
                PuntoCritico(
                    id = doc.id,
                    direccion = doc.getString("direccion").orEmpty().ifBlank { "Chimbote, Áncash" },
                    referencia = doc.getString("tipo").orEmpty().ifBlank { "Foco reportado" },
                    distanciaMetros = 0,
                    cantidadReportes = 1,
                    severidad = parseSeveridad(doc.getString("severidad")),
                    fotoUrl = doc.getString("fotoUrl").orEmpty()
                )
            }

        val nombre = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Vecino"

        return ResumenCiudadano(
            puntosActivos = activos,
            tusReportes = mios,
            resueltos = resueltos,
            focosCerca = puntos.size,
            puntosCercanos = puntos,
            nombre = nombre
        )
    }

    /**
     * Focos activos con coordenadas para el mapa (C08) y el heatmap del Inicio.
     * Solo PENDIENTES (RECIBIDO/EN_PROCESO), igual que las zonas: así el mapa y la
     * lista de zonas cuentan lo mismo, y no aparecen pines de duplicado/anulado ni
     * de estados viejos. La zona se deriva igual que en zonasAfectadas.
     */
    suspend fun obtenerFocosMapa(): List<FocoMapa> {
        val docs = db.collection(COLECCION).get().await().documents
        return docs
            .filter { parseEstado(it.getString("estado")).esPendiente }
            .mapNotNull { doc ->
                val lat = doc.getDouble("latitud") ?: return@mapNotNull null
                val lng = doc.getDouble("longitud") ?: return@mapNotNull null
                val zona = doc.getString("zona")?.trim()?.ifBlank { null }
                    ?: doc.getString("direccion").orEmpty().substringBefore(",").trim().ifBlank { "Chimbote" }
                FocoMapa(
                    id = doc.id,
                    latitud = lat,
                    longitud = lng,
                    severidad = parseSeveridad(doc.getString("severidad")),
                    direccion = doc.getString("direccion").orEmpty(),
                    tipo = doc.getString("tipo").orEmpty(),
                    ticket = doc.getString("ticket").orEmpty(),
                    estado = parseEstado(doc.getString("estado")),
                    fotoUrl = doc.getString("fotoUrl").orEmpty(),
                    zona = zona,
                    confianza = (doc.getLong("confianza") ?: 0L).toInt(),
                    areaM2 = doc.getDouble("areaM2") ?: 0.0,
                    autor = doc.getString("autor").orEmpty(),
                    autorNivel = (doc.getLong("autorNivel") ?: 1L).toInt()
                )
            }
    }

    /**
     * Bandeja de moderación del administrador (A02): SOLO los reportes accionables
     * (pendientes: RECIBIDO o EN_PROCESO), del más nuevo al más viejo. Los terminales
     * (resuelto/duplicado/anulado) salen de la bandeja pero siguen en la BD para la
     * reportería. Trae coordenadas y hora para decidir. La lectura no necesita rol
     * admin (es comunitaria); las ACCIONES de abajo sí.
     */
    suspend fun alertasAdmin(): List<AlertaAdmin> {
        val docs = db.collection(COLECCION).get().await().documents
        return docs
            .filter { parseEstado(it.getString("estado")).esPendiente }
            .sortedByDescending { it.getTimestamp("creadoEn")?.toDate()?.time ?: 0L }
            .map { doc ->
                val estado = parseEstado(doc.getString("estado"))
                val fecha = doc.getTimestamp("creadoEn")?.toDate()
                AlertaAdmin(
                    id = doc.id,
                    direccion = doc.getString("direccion").orEmpty().ifBlank { "Chimbote, Áncash" },
                    tiempoTexto = tiempoRelativo(fecha),
                    agrupados = 1, // sin lógica de agrupación todavía: 1 reporte por foco
                    severidad = parseSeveridad(doc.getString("severidad")),
                    nuevo = estado == EstadoReporte.RECIBIDO,
                    ticket = doc.getString("ticket").orEmpty(),
                    estado = estado,
                    fotoUrl = doc.getString("fotoUrl").orEmpty(),
                    latitud = doc.getDouble("latitud") ?: 0.0,
                    longitud = doc.getDouble("longitud") ?: 0.0,
                    fechaHoraTexto = formatearFecha(fecha),
                    tipo = doc.getString("tipo").orEmpty(),
                    descripcion = doc.getString("descripcion").orEmpty(),
                    areaM2 = doc.getDouble("areaM2") ?: 0.0,
                    confianza = (doc.getLong("confianza") ?: 0L).toInt()
                )
            }
    }

    /** Validar un reporte: lo mueve a EN_PROCESO. Requiere rol admin (reglas). */
    suspend fun validarReporte(id: String) {
        db.collection(COLECCION).document(id).update("estado", EstadoReporte.EN_PROCESO.name).await()
    }

    /** Aprobar resuelto: el foco fue limpiado -> RESUELTO. Requiere rol admin. */
    suspend fun resolverReporte(id: String) {
        db.collection(COLECCION).document(id).update("estado", EstadoReporte.RESUELTO.name).await()
    }

    /** Marcar como duplicado. Requiere rol admin (reglas). */
    suspend fun marcarDuplicado(id: String) {
        db.collection(COLECCION).document(id).update("estado", EstadoReporte.DUPLICADO.name).await()
    }

    /**
     * Anular un reporte (troll/falso). NO lo borra: lo marca ANULADO para conservar
     * el rastro en la auditoría/reportería. Requiere rol admin (reglas).
     */
    suspend fun anularReporte(id: String) {
        db.collection(COLECCION).document(id).update("estado", EstadoReporte.ANULADO.name).await()
    }

    /** Actualizar el nivel (severidad) del reporte. Requiere rol admin (reglas). */
    suspend fun actualizarSeveridad(id: String, severidad: Severidad) {
        db.collection(COLECCION).document(id).update("severidad", severidad.name).await()
    }

    /**
     * Zonas afectadas de puntos críticos (A03): agrupa los focos activos (no
     * terminales) por la primera parte de la dirección y devuelve las 3 con más
     * focos, pintadas por su peor severidad. Alimenta la lista bajo el mapa.
     */
    suspend fun zonasAfectadas(): List<ZonaAfectada> {
        val docs = db.collection(COLECCION).get().await().documents
        return docs
            .filter { parseEstado(it.getString("estado")).esPendiente }
            // Agrupa por la ZONA real guardada (barrio/distrito del Geocoder). Los
            // reportes viejos (seed) no tienen ese campo: caen al método anterior
            // (primera parte de la dirección), que para ellos funciona bien.
            .groupBy { doc ->
                doc.getString("zona")?.trim()?.ifBlank { null }
                    ?: doc.getString("direccion").orEmpty().substringBefore(",").trim().ifBlank { "Chimbote" }
            }
            .map { (zona, lista) ->
                // Centro de la zona = promedio de las coordenadas de sus focos (para
                // enfocar el mapa al tocarla). Si algún foco no trae coords, se ignora.
                val lats = lista.mapNotNull { it.getDouble("latitud") }
                val lngs = lista.mapNotNull { it.getDouble("longitud") }
                ZonaAfectada(
                    nombre = zona,
                    focos = lista.size,
                    severidad = peorSeveridad(lista.map { parseSeveridad(it.getString("severidad")) }),
                    latitud = if (lats.isEmpty()) 0.0 else lats.average(),
                    longitud = if (lngs.isEmpty()) 0.0 else lngs.average()
                )
            }
            .sortedByDescending { it.focos }
        // Sin recorte: devolvemos TODAS las zonas ordenadas. La vista decide cuántas
        // mostrar (top 3 por defecto, todas las que matcheen al buscar).
    }

    /**
     * Notificaciones del vecino (C16) derivadas del estado REAL de SUS reportes:
     * una por reporte, con el mensaje según su estado (recibido/validado/resuelto/
     * unificado). Reemplaza la lista hardcodeada del mock.
     */
    suspend fun notificacionesCiudadano(): List<Notificacion> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val limite24h = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return db.collection(COLECCION).whereEqualTo("userId", uid).get().await().documents
            .sortedByDescending { it.getTimestamp("creadoEn")?.toDate()?.time ?: 0L }
            .map { doc ->
                val estado = parseEstado(doc.getString("estado"))
                val ticket = doc.getString("ticket").orEmpty()
                val direccion = doc.getString("direccion").orEmpty().ifBlank { "tu zona" }
                val fecha = doc.getTimestamp("creadoEn")?.toDate()
                val (tipo, texto) = when (estado) {
                    EstadoReporte.RESUELTO ->
                        TipoNotificacion.RESUELTO to "¡Resuelto! El foco de $direccion fue limpiado."
                    EstadoReporte.EN_PROCESO ->
                        TipoNotificacion.VALIDADO to "Tu reporte $ticket fue validado por la administración."
                    EstadoReporte.DUPLICADO ->
                        TipoNotificacion.UNIFICADO to "Tu reporte $ticket fue unificado con el de otros vecinos."
                    EstadoReporte.ANULADO ->
                        TipoNotificacion.UNIFICADO to "Tu reporte $ticket fue anulado por la administración."
                    EstadoReporte.RECIBIDO ->
                        TipoNotificacion.VALIDADO to "Recibimos tu reporte $ticket. Pronto lo revisaremos."
                }
                Notificacion(
                    tipo = tipo,
                    texto = texto,
                    tiempoTexto = tiempoRelativo(fecha),
                    grupo = if ((fecha?.time ?: 0L) >= limite24h) "HOY" else "ESTA SEMANA"
                )
            }
    }

    /** La severidad más alta de un grupo (para pintar la zona por su peor foco). */
    private fun peorSeveridad(severidades: List<Severidad>): Severidad = when {
        severidades.contains(Severidad.ALTA) -> Severidad.ALTA
        severidades.contains(Severidad.MEDIA) -> Severidad.MEDIA
        else -> Severidad.BAJA
    }

    /**
     * Perfil REAL del vecino logueado (C17): nombre del doc `usuarios`, conteos de
     * sus reportes en Firestore, y una gamificación derivada de su actividad real
     * (no inventada): 20 puntos por reporte + 30 por reporte resuelto, niveles cada
     * 200 puntos. Reemplaza al mock que mostraba siempre "Fernando Cholán".
     */
    suspend fun obtenerPerfil(): PerfilCiudadano {
        val user = auth.currentUser
        val uid = user?.uid

        val nombreGuardado = if (uid != null) {
            runCatching { db.collection("usuarios").document(uid).get().await().getString("nombre") }.getOrNull()
        } else null
        val nombre = (nombreGuardado ?: user?.displayName ?: user?.email?.substringBefore("@") ?: "Vecino")
            .ifBlank { "Vecino" }

        val docs = if (uid != null) {
            db.collection(COLECCION).whereEqualTo("userId", uid).get().await().documents
        } else emptyList()
        val total = docs.size
        val resueltos = docs.count { doc -> doc.getString("estado") == EstadoReporte.RESUELTO.name }

        val puntos = total * 20 + resueltos * 30
        val nivel = (puntos / 200 + 1).coerceAtMost(NIVEL_MAX) // 5 niveles como tope
        val esMax = nivel == NIVEL_MAX
        val objetivo = nivel * 200
        val faltan = (objetivo - puntos).coerceAtLeast(0)

        return PerfilCiudadano(
            iniciales = iniciales(nombre),
            nombre = nombre,
            rol = tituloNivel(nivel),
            reportes = total,
            resueltos = resueltos,
            puntosMinka = puntos,
            nivel = nivel,
            nivelTexto = "Nivel $nivel · ${tituloNivel(nivel)}",
            puntosNivelActual = if (esMax) objetivo else puntos,
            puntosNivelObjetivo = objetivo,
            faltanTexto = if (esMax) "¡Alcanzaste el nivel máximo!"
            else "Te faltan $faltan puntos para subir de nivel"
        )
    }

    /** Iniciales a partir del nombre ("Fernando Cholán" -> "FC"). */
    private fun iniciales(nombre: String): String {
        val partes = nombre.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            partes.isEmpty() -> "?"
            partes.size == 1 -> partes[0].take(2).uppercase()
            else -> (partes[0].first().toString() + partes[1].first()).uppercase()
        }
    }

    private fun tituloNivel(nivel: Int) = when (nivel) {
        1 -> "Brote Ciudadano"
        2 -> "Protector Verde"
        3 -> "Forjador Urbano"
        4 -> "Héroe Eco-Tecnológico"
        else -> "Guardián Global"
    }

    /** Tiempo relativo legible ("Hace 4 min") para la bandeja del admin. */
    private fun tiempoRelativo(fecha: Date?): String {
        if (fecha == null) return "—"
        val minutos = (System.currentTimeMillis() - fecha.time) / 60_000
        return when {
            minutos < 1 -> "Recién"
            minutos < 60 -> "Hace $minutos min"
            minutos < 1_440 -> "Hace ${minutos / 60} h"
            else -> "Hace ${minutos / 1_440} d"
        }
    }

    /**
     * Reporte municipal filtrado (A06): los reportes cuyo `creadoEn` cae en
     * [desde, hasta] (epoch ms, inclusive) y —si se indica— con esa severidad.
     * Del más nuevo al más viejo. Es la FUENTE ÚNICA que alimenta la pantalla, el
     * CSV y el PDF: una sola lectura, tres presentaciones. `severidad = null` =
     * todos los niveles.
     */
    suspend fun reportesFiltrados(desde: Long, hasta: Long, severidad: Severidad?): List<FilaReporte> {
        val docs = db.collection(COLECCION).get().await().documents
        return docs
            .filter { doc ->
                val t = doc.getTimestamp("creadoEn")?.toDate()?.time ?: return@filter false
                t in desde..hasta
            }
            .filter { severidad == null || parseSeveridad(it.getString("severidad")) == severidad }
            .sortedByDescending { it.getTimestamp("creadoEn")?.toDate()?.time ?: 0L }
            .map { doc ->
                FilaReporte(
                    ticket = doc.getString("ticket").orEmpty(),
                    direccion = doc.getString("direccion").orEmpty(),
                    tipo = doc.getString("tipo").orEmpty(),
                    severidad = parseSeveridad(doc.getString("severidad")),
                    estado = parseEstado(doc.getString("estado")),
                    fechaHoraTexto = formatearFecha(doc.getTimestamp("creadoEn")?.toDate()),
                    latitud = doc.getDouble("latitud") ?: 0.0,
                    longitud = doc.getDouble("longitud") ?: 0.0
                )
            }
    }

    /**
     * Construye el CSV municipal a partir de filas ya filtradas (función pura, sin
     * Firestore). Abre en Excel; los campos van entre comillas (las direcciones
     * traen comas) y las comillas internas se duplican (estándar CSV).
     */
    fun csvDe(filas: List<FilaReporte>): String {
        val sb = StringBuilder()
        sb.append("Ticket,Direccion,Tipo,Severidad,Estado,Fecha,Latitud,Longitud\n")
        filas.forEach { f ->
            val fila = listOf(
                f.ticket,
                f.direccion,
                f.tipo,
                f.severidad.name,
                f.estado.etiqueta,
                f.fechaHoraTexto,
                f.latitud.toString(),
                f.longitud.toString()
            ).joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
            sb.append(fila).append("\n")
        }
        return sb.toString()
    }

    private fun formatearFecha(fecha: Date?): String =
        fecha?.let { SimpleDateFormat("dd MMM · HH:mm", Locale("es")).format(it) } ?: "—"

    private fun parseSeveridad(valor: String?) =
        runCatching { Severidad.valueOf(valor ?: "") }.getOrDefault(Severidad.MEDIA)

    private fun parseEstado(valor: String?) =
        runCatching { EstadoReporte.valueOf(valor ?: "") }.getOrDefault(EstadoReporte.RECIBIDO)

    /**
     * Nivel actual del usuario logueado (1..5), con la misma fórmula que el perfil:
     * 20 pts por reporte + 30 por resuelto, un nivel cada 200. Para sellar la insignia
     * del AUTOR en el reporte al crearlo. Una sola lectura de SUS reportes.
     */
    suspend fun nivelUsuario(): Int {
        val uid = auth.currentUser?.uid ?: return 1
        val docs = db.collection(COLECCION).whereEqualTo("userId", uid).get().await().documents
        val total = docs.size
        val resueltos = docs.count { it.getString("estado") == EstadoReporte.RESUELTO.name }
        val puntos = total * 20 + resueltos * 30
        return (puntos / 200 + 1).coerceAtMost(NIVEL_MAX)
    }

    companion object {
        private const val COLECCION = "reportes"
        private const val NIVEL_MAX = 5
    }
}
