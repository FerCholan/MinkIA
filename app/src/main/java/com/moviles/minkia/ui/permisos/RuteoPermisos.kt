package com.moviles.minkia.ui.permisos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.moviles.minkia.data.local.PreferenciasRepository
import kotlinx.coroutines.flow.first

/**
 * Decide a dónde va el ciudadano tras autenticarse. La pantalla de Permisos
 * (C06) es de "priming" y se muestra solo la PRIMERA vez: si ya se vio antes, o
 * si los permisos clave (cámara y ubicación) ya están concedidos, se va directo
 * a la app. Los permisos faltantes igual se vuelven a pedir en contexto cuando
 * hagan falta (cámara al fotografiar, ubicación al geolocalizar).
 *
 * Centralizado acá para que Login y Registro compartan exactamente la misma
 * regla, sin duplicarla.
 */
object RuteoPermisos {

    private val PERMISOS_CLAVE = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    fun permisosClaveConcedidos(context: Context): Boolean =
        PERMISOS_CLAVE.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * A dónde debe ir el ciudadano recién logueado. Antes de este refactor
     * devolvía la Activity destino (`Class<out Activity>`, MainActivity o
     * PermisosActivity). Con Navigation Component eso ya no alcanza: Permisos
     * pasó a ser un fragment DENTRO del propio grafo de auth (nav_auth),
     * mientras que Inicio sigue siendo una Activity real fuera del grafo. Por
     * eso el resultado ahora es este enum: cada camino necesita un mecanismo
     * de navegación distinto (findNavController().navigate(...) vs
     * startActivity(...)), y esa decisión le corresponde al fragment que
     * llama (LoginFragment/RegistroFragment), no a este objeto. La REGLA en
     * sí (la condición del if de abajo) es exactamente la misma que antes.
     */
    enum class DestinoCiudadano { INICIO, PERMISOS }

    /** Permisos solo si nunca se vio esa pantalla y falta algún permiso clave. */
    suspend fun destinoCiudadano(
        context: Context,
        prefs: PreferenciasRepository
    ): DestinoCiudadano =
        if (prefs.permisosVistos.first() || permisosClaveConcedidos(context)) {
            DestinoCiudadano.INICIO
        } else {
            DestinoCiudadano.PERMISOS
        }
}
