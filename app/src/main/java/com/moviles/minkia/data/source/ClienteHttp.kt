package com.moviles.minkia.data.source

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP único de la app (hoy lo usa la subida de fotos a Cloudinary).
 *
 * Existe como pieza propia, y no adentro de quien lo usa, por [reiniciarConexiones]:
 * el pool de conexiones tiene que poder vaciarse desde afuera cuando el vecino
 * cambia de red, y para eso hace falta que haya UN pool conocido en vez de uno
 * escondido en cada fuente de datos.
 */
object ClienteHttp {

    /**
     * Timeouts explícitos. Los de fábrica de OkHttp son 10 s para TODO, incluida
     * la escritura del cuerpo: subir una foto con datos móviles lentos los pasa
     * de largo sin despeinarse y la subida moría con SocketTimeoutException.
     * Conectar sigue siendo corto (si no hay red, que falle rápido); mandar los
     * bytes es lo que necesita aire.
     */
    val instancia: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // Que reintente solo si la conexión se cae a mitad. Es el default de
        // OkHttp; se deja escrito porque acá NO es un detalle: es media defensa
        // contra el socket que quedó muerto al cambiar de red.
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Tira las conexiones guardadas en el pool.
     *
     * Al cambiar de wifi a datos (o al revés) los sockets abiertos sobre la red
     * anterior quedan muertos, pero OkHttp no lo sabe: los tiene guardados para
     * reusarlos y se los entrega a la siguiente petición, que falla. Ese era el
     * "no hay internet" justo después de tomar una foto habiendo salido del
     * wifi. Vaciando el pool, la próxima petición abre una conexión nueva sobre
     * la red nueva.
     *
     * No cancela nada en curso: solo descarta las conexiones ociosas.
     */
    fun reiniciarConexiones() {
        instancia.connectionPool.evictAll()
    }
}
