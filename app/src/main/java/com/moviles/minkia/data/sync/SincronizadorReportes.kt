package com.moviles.minkia.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.source.ReporteFirestoreDataSource
import com.moviles.minkia.data.source.ResultadoEnvio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reintenta enviar los reportes de la cola local. Solo actúa si HAY internet, así
 * la escritura a Firestore no queda colgada esperando al servidor. Cada envío tiene
 * un timeout de seguridad; el que se envía se saca de la cola, el que falla se
 * conserva para el próximo intento. Se dispara en tres momentos: al abrir la app,
 * al detectar que volvió la conexión (MinkIaApplication) y con el botón manual.
 *
 * Singleton (objeto): coordina una única cola. Un Mutex evita dos sincronizaciones
 * simultáneas (ej. app-start y network-callback a la vez).
 */
object SincronizadorReportes {

    private lateinit var appContext: Context

    /**
     * Fuente de envío. Es `internal var` y no `private val` por una única razón:
     * un `private val` de un `object` compila a un campo static final, imposible
     * de sustituir (ni por reflexión) en un test, y la política de reintentos de
     * abajo —qué reporte se conserva y cuál se abandona— es lógica que SÍ hay que
     * poder probar. En producción no cambia nada: se usa esta misma instancia.
     */
    internal var dataSource = ReporteFirestoreDataSource()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Dispara una sincronización en segundo plano (no bloquea al que llama). */
    fun disparar() {
        scope.launch { sincronizar() }
    }

    /**
     * Intenta enviar todos los pendientes. Devuelve cuántos se enviaron. Si no hay
     * internet, no intenta nada (0). Reentrante-seguro por el Mutex.
     */
    suspend fun sincronizar(): Int {
        if (!hayInternet()) return 0
        return mutex.withLock {
            var enviados = 0
            for (p in ColaReportes.listar()) {
                if (!hayInternet()) break // la conexión pudo caerse a mitad
                val resultado = withTimeoutOrNull(TIMEOUT_MS) { dataSource.enviarReporte(p) }
                    ?: ResultadoEnvio.REINTENTAR // timeout: transitorio, se conserva
                when (resultado) {
                    ResultadoEnvio.ENVIADO -> {
                        ColaReportes.remover(p.id)
                        enviados++
                    }
                    // Fallo permanente (reglas, dato inválido): sacar de la cola para
                    // no reintentar un envío irrecuperable en cada reconexión.
                    ResultadoEnvio.DESCARTAR -> ColaReportes.remover(p.id)
                    ResultadoEnvio.REINTENTAR -> Unit // se conserva para el próximo intento
                }
            }
            enviados
        }
    }

    /** Hay conexión con capacidad de internet real (no solo "conectado a un wifi"). */
    fun hayInternet(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val red = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(red) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private const val TIMEOUT_MS = 20_000L
}
