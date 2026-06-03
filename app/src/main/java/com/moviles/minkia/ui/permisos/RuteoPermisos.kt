package com.moviles.minkia.ui.permisos

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.ui.MainActivity
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

    /** Activity destino del ciudadano: Permisos solo si nunca se vio y falta algún permiso clave. */
    suspend fun destinoCiudadano(
        context: Context,
        prefs: PreferenciasRepository
    ): Class<out Activity> =
        if (prefs.permisosVistos.first() || permisosClaveConcedidos(context)) {
            MainActivity::class.java
        } else {
            PermisosActivity::class.java
        }
}
