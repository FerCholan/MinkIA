package com.moviles.minkia.data.local

import android.content.Context
import com.moviles.minkia.data.model.ReportePendiente
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cola LOCAL de reportes pendientes de enviar (offline-first). Persiste en un
 * archivo JSON en filesDir (sobrevive a reinicios) y las fotos en una subcarpeta
 * propia (cacheDir podría vaciarse). Expone la cantidad como StateFlow para que la
 * UI (Configuración) muestre el número REAL, no uno inventado.
 *
 * Es un singleton (objeto) porque el estado —la cola y su contador— es único en la
 * app. Se inicializa una vez en MinkIaApplication.
 */
object ColaReportes {

    private lateinit var appContext: Context
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cantidad = MutableStateFlow(0)
    /** Cantidad de reportes en la cola. La UI la observa para el contador real. */
    val cantidad: StateFlow<Int> = _cantidad.asStateFlow()

    /**
     * Se llama desde Application.onCreate, o sea en el HILO PRINCIPAL. Por eso la
     * carga inicial del contador se dispara en segundo plano: leer y parsear el
     * JSON de la cola es IO de disco, y hacerlo acá mismo demoraba el arranque de
     * la app en proporción a lo que hubiera encolado (violación de StrictMode y,
     * con la cola grande, riesgo de ANR).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch { recargarContador() }
    }

    /**
     * Relee la cola de disco y actualiza el contador. Suspendida y pública dentro
     * del módulo para que el arranque no la espere pero los tests sí puedan.
     */
    internal suspend fun recargarContador(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { _cantidad.value = leer().size }
    }

    private fun archivo(): File = File(appContext.filesDir, "reportes_pendientes.json")
    private fun carpetaFotos(): File = File(appContext.filesDir, "pendientes").apply { mkdirs() }

    /**
     * Encola un reporte. Si trae foto, la COPIA a un lugar persistente (la original
     * vive en cacheDir y podría borrarse). Actualiza el contador.
     */
    suspend fun encolar(p: ReportePendiente): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val fotoPersistente = p.fotoPath?.let { copiarFoto(it, p.id) }
            val lista = leer().toMutableList()
            lista.add(p.copy(fotoPath = fotoPersistente))
            escribir(lista)
            _cantidad.value = lista.size
        }
    }

    /** Devuelve una copia de los pendientes actuales. */
    suspend fun listar(): List<ReportePendiente> = withContext(Dispatchers.IO) {
        mutex.withLock { leer() }
    }

    /** Quita un reporte de la cola (ya enviado) y borra su foto local. */
    suspend fun remover(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val lista = leer()
            lista.firstOrNull { it.id == id }?.fotoPath?.let { runCatching { File(it).delete() } }
            val restante = lista.filterNot { it.id == id }
            escribir(restante)
            _cantidad.value = restante.size
        }
    }

    private fun copiarFoto(origen: String, id: String): String? = runCatching {
        val src = File(origen)
        if (!src.exists()) return@runCatching null
        val destino = File(carpetaFotos(), "$id.jpg")
        src.copyTo(destino, overwrite = true)
        destino.absolutePath
    }.getOrNull()

    // --- Persistencia JSON (org.json, sin dependencias extra) ---

    private fun leer(): List<ReportePendiente> = runCatching {
        val f = archivo()
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { desdeJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun escribir(lista: List<ReportePendiente>) {
        val arr = JSONArray()
        lista.forEach { arr.put(aJson(it)) }
        archivo().writeText(arr.toString())
    }

    private fun aJson(p: ReportePendiente) = JSONObject().apply {
        put("id", p.id); put("userId", p.userId); put("ticket", p.ticket)
        put("tipo", p.tipo); put("severidad", p.severidad); put("descripcion", p.descripcion)
        put("direccion", p.direccion); put("zona", p.zona)
        put("latitud", p.latitud); put("longitud", p.longitud)
        put("areaM2", p.areaM2); put("confianza", p.confianza)
        put("fotoPath", p.fotoPath ?: JSONObject.NULL); put("creadoEn", p.creadoEn)
        put("autor", p.autor); put("autorNivel", p.autorNivel)
    }

    private fun desdeJson(o: JSONObject) = ReportePendiente(
        id = o.getString("id"),
        userId = o.getString("userId"),
        ticket = o.optString("ticket"),
        tipo = o.optString("tipo"),
        severidad = o.optString("severidad"),
        descripcion = o.optString("descripcion"),
        direccion = o.optString("direccion"),
        zona = o.optString("zona"),
        latitud = o.optDouble("latitud", 0.0),
        longitud = o.optDouble("longitud", 0.0),
        areaM2 = o.optDouble("areaM2", 0.0),
        confianza = o.optInt("confianza", 0),
        fotoPath = if (o.isNull("fotoPath")) null else o.optString("fotoPath"),
        creadoEn = o.optLong("creadoEn", 0L),
        autor = o.optString("autor"),
        autorNivel = o.optInt("autorNivel", 1)
    )
}
