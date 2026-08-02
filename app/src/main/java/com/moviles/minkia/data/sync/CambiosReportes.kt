package com.moviles.minkia.data.sync

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.atomic.AtomicLong

/**
 * Aviso de "los reportes del servidor cambiaron por algo que hizo ESTA app".
 *
 * Existe por un caso que no se arregla recargando al volver a la pantalla: el
 * reporte que sale de la COLA. Cuando el vecino cambia de red, su reporte se
 * encola y [SincronizadorReportes] lo manda apenas la conexión vuelve, que puede
 * ser mientras él está mirando el mapa. En ese momento el reporte YA existe en el
 * servidor, pero ninguna pantalla se enteró: no hubo navegación, nadie volvió a
 * ninguna parte, y la lista que se ve es la de antes. De ahí el "tengo que cerrar
 * la app y volver a entrar para que aparezca".
 *
 * El contador no dice QUÉ cambió, solo que algo cambió. Alcanza: las pantallas
 * releen todo igual, y así el aviso no se acopla a la forma de los datos.
 *
 * Ojo con lo que este aviso NO cubre: los reportes de OTROS vecinos, que
 * aparecen en el servidor sin que esta app se entere. Para eso las pantallas
 * recargan igual cada vez que vuelven al frente (ver sus onResume). Los dos
 * mecanismos son complementarios, no repetidos: este cubre "cambió mientras
 * mirabas", el otro cubre "cambió mientras no mirabas".
 */
object CambiosReportes {

    // AtomicLong y no el valor del LiveData: notificar() puede llegar desde el
    // hilo de la sincronización y desde el del envío directo a la vez, y leer
    // _version.value para sumarle uno perdería avisos en esa carrera.
    private val contador = AtomicLong(0)

    private val _version = MutableLiveData(0L)

    /** Sube de a uno cada vez que esta app deja un reporte nuevo en el servidor. */
    val version: LiveData<Long> = _version

    /** Valor actual, para quien necesita compararlo sin observar. */
    fun actual(): Long = contador.get()

    /**
     * Avisa que hay un reporte nuevo confirmado en el servidor. postValue (no
     * setValue) porque esto se llama desde hilos de fondo: el envío directo y el
     * sincronizador de la cola.
     */
    fun notificar() {
        _version.postValue(contador.incrementAndGet())
    }
}
