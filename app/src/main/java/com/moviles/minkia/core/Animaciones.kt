package com.moviles.minkia.core

import android.app.Activity
import android.view.View

/**
 * Animación de entrada sutil y reutilizable: la vista aparece con un fundido y
 * un leve desplazamiento hacia arriba. Pensada para el contenido de una pantalla
 * apenas llegan los datos (estado Success), para que no aparezca de golpe.
 */
fun View.animarEntrada(duracionMs: Long = 320L, desplazamientoY: Float = 24f) {
    alpha = 0f
    translationY = desplazamientoY
    animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duracionMs)
        .start()
}

/**
 * Transición de cross-fade entre Activities. Como las pantallas de entrada
 * comparten el mismo fondo andino, fundir solo el contenido hace que el fondo
 * parezca quedarse quieto: la navegación se siente fluida, de una sola pieza.
 * Llamar justo después de startActivity().
 */
@Suppress("DEPRECATION")
fun Activity.transicionFundido() {
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
}
