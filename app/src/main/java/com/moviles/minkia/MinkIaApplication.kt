package com.moviles.minkia

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.appcompat.app.AppCompatDelegate
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.sync.SincronizadorReportes

/**
 * Application de MinkIA. Fija el modo claro (identidad cálida) e inicializa el
 * OFFLINE-FIRST de reportes: la cola local y su sincronizador, más un callback de
 * red que reintenta el envío APENAS vuelve internet (sin que el vecino haga nada).
 */
class MinkIaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        ColaReportes.init(this)
        SincronizadorReportes.init(this)
        registrarCallbackRed()
        // Al abrir la app: intenta enviar lo que haya quedado de una sesión anterior.
        SincronizadorReportes.disparar()
    }

    /** Cuando vuelve la conexión, dispara la sincronización de la cola. */
    private fun registrarCallbackRed() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                SincronizadorReportes.disparar()
            }
        })
    }
}
