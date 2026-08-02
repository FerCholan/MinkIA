package com.moviles.minkia.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.minkia.data.source.ClienteHttp

/**
 * Conectividad de la app, en un solo lugar, y lo que hay que hacer cuando el
 * vecino CAMBIA de red (sale del wifi de su casa y queda con datos móviles, o
 * al revés). Ese cambio no es lo mismo que quedarse sin internet, y tratarlo
 * como si lo fuera era el problema: la app quedaba diciendo "no hay internet"
 * con el teléfono navegando perfecto.
 *
 * Cuando la red por defecto cambia, la anterior NO se apaga con elegancia: se
 * muere y deja tres cosas colgadas que hay que atender, y ninguna se arregla
 * sola en el momento:
 *
 * 1. Los sockets ya abiertos quedan muertos. OkHttp guarda conexiones en un
 *    pool para reusarlas, así que la PRIMERA subida después del cambio agarra
 *    una conexión de la red vieja y falla. Es exactamente lo que pasaba al
 *    tomar una foto y mandarla justo después de salir del wifi: la foto no
 *    subía y el reporte terminaba en la cola con cara de "sin internet".
 * 2. El canal de Firestore queda atado a la red vieja y el SDK puede tardar en
 *    darse cuenta; mientras tanto lee de su cache y las escrituras esperan.
 * 3. Lo que quedó encolado hay que reintentarlo AHORA, no en el próximo
 *    arranque.
 *
 * Por eso el cambio de red se atiende una sola vez y para toda la app: se
 * tiran las conexiones viejas, se reconecta Firestore, se dispara la cola y se
 * publica el estado nuevo en [conectado] para que la UI reaccione sola.
 *
 * Singleton (objeto) porque el callback del sistema se registra UNA vez, al
 * arrancar el proceso, y vale para todas las pantallas.
 */
object EstadoRed {

    private lateinit var appContext: Context

    /**
     * Red por defecto actual. Sirve para distinguir "cambié de red" (wifi a
     * datos) de "la misma red me avisa cualquier otra cosa": el sistema notifica
     * seguido, y tirar las conexiones en cada aviso sería castigar la batería y
     * la latencia sin motivo.
     */
    private var redActual: Network? = null

    private val _conectado = MutableLiveData<Boolean>()

    /**
     * Si hay o no una red utilizable, observable. La UI se suscribe a esto en
     * vez de preguntar a mano cada vez que la pantalla vuelve al frente: así el
     * aviso de "sin conexión" aparece y desaparece SOLO, en el momento real en
     * que la red se cae o vuelve, sin que el vecino tenga que salir y entrar.
     */
    val conectado: LiveData<Boolean> = _conectado

    fun init(context: Context) {
        appContext = context.applicationContext
        redActual = gestor()?.activeNetwork
        _conectado.postValue(consultar())
        registrarCallback()
    }

    /**
     * Consulta puntual, para el código que necesita una respuesta ya y no puede
     * observar [conectado]. Ver [consultar].
     */
    fun hayInternet(): Boolean = consultar()

    private fun gestor(): ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Alcanza con que la red declare capacidad de internet.
     *
     * A propósito NO se exige NET_CAPABILITY_VALIDATED, que es lo que se pedía
     * antes: esa capacidad la otorga Android recién DESPUÉS de que su sonda
     * contra connectivitycheck.gstatic.com responde. Con datos móviles esa sonda
     * tarda, la bloquean algunos operadores, o nunca se marca en redes con proxy
     * o portal cautivo. El teléfono navegaba perfecto y MinkIA se declaraba sin
     * internet, encolaba todo y no mandaba nada. Con la wifi de casa la sonda
     * pasa al toque: por eso en el emulador y en la wifi del desarrollo no se
     * veía, y en la calle sí.
     */
    private fun consultar(): Boolean {
        val cm = gestor() ?: return false
        val red = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(red) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * registerDefaultNetworkCallback avisa de la red que la app está usando de
     * verdad, así que un salto de wifi a datos llega acá como un onAvailable con
     * una Network distinta. Es justo el evento que hay que atender.
     */
    private fun registrarCallback() {
        val cm = gestor() ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val esOtraRed = redActual != network
                redActual = network
                _conectado.postValue(true)
                // Solo si de verdad cambiamos de red: ver el KDoc de redActual.
                if (esOtraRed) rehacerConexiones()
            }

            override fun onLost(network: Network) {
                // Al saltar de wifi a datos, el onLost de la red vieja puede
                // llegar DESPUÉS del onAvailable de la nueva. Publicar "false" a
                // ciegas acá haría parpadear el aviso de sin conexión en plena
                // transición, que es cuando más confunde. Se vuelve a consultar
                // el estado real en vez de creerle al evento.
                if (redActual == network) redActual = null
                _conectado.postValue(consultar())
            }
        })
    }

    /**
     * Lo que hay que rehacer cuando la red cambió debajo de los pies. El orden
     * importa: primero se tira lo viejo, después se reconecta, y al final se
     * reintenta lo pendiente sobre una conexión ya sana.
     */
    private fun rehacerConexiones() {
        ClienteHttp.reiniciarConexiones()
        reconectarFirestore()
        SincronizadorReportes.disparar()
    }

    /**
     * Obliga a Firestore a rearmar su conexión sobre la red nueva. El apagar y
     * prender es el mecanismo que el propio SDK expone para esto; no se pierde
     * nada en el camino, porque con la red deshabilitada las escrituras quedan
     * esperando en local y salen al volver a habilitarla.
     */
    private fun reconectarFirestore() {
        val db = FirebaseFirestore.getInstance()
        db.disableNetwork().addOnCompleteListener { db.enableNetwork() }
    }
}
