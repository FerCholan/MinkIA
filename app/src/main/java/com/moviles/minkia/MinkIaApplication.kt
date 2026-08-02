package com.moviles.minkia

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.sync.EstadoRed
import com.moviles.minkia.data.sync.SincronizadorReportes
import com.moviles.minkia.data.sync.SincronizarReportesWorker

/**
 * Application de MinkIA. Fija el modo claro (identidad cálida) e inicializa el
 * OFFLINE-FIRST de reportes: la cola local, su sincronizador y la vigilancia de
 * la red, que reintenta el envío apenas vuelve la conexión (sin que el vecino
 * haga nada) y rearma las conexiones cuando se cambia de wifi a datos.
 */
class MinkIaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        ColaReportes.init(this)
        SincronizadorReportes.init(this)
        // Después del sincronizador: al registrar el callback, EstadoRed puede
        // disparar una sincronización enseguida (ver EstadoRed.rehacerConexiones).
        EstadoRed.init(this)
        // Al abrir la app: intenta enviar lo que haya quedado de una sesión anterior.
        SincronizadorReportes.disparar()
        // Y deja el reintento agendado en el sistema, que es lo único que sigue vivo
        // cuando Android mata el proceso: sin esto, una cola con reportes podía
        // quedarse esperando a que el vecino volviera a abrir la app.
        SincronizarReportesWorker.programar(this)
    }
}
